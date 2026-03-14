#!/usr/bin/env python3
"""
Playnite-to-Apollo Sync Tool

Reads the Playnite game library and writes matching entries into Apollo's
apps.json so every installed game is launchable and streamable via the
Moonlight protocol.

Usage:
    python playnite_apollo_sync.py [--config config.json] [--watch] [--dry-run]
"""

import argparse
import hashlib
import json
import logging
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)


def expand_path(path: str) -> Path:
    """Expand environment variables and user home in a path."""
    return Path(os.path.expandvars(os.path.expanduser(path)))


def generate_app_id(source: str, game_id: str) -> str:
    """Generate a deterministic Apollo app ID from source and game ID."""
    return hashlib.sha256(f"{source}:{game_id}".encode()).hexdigest()[:16]


def build_launch_command(game: dict) -> Optional[str]:
    """Map a Playnite game to the correct Apollo launch command.

    Returns the launch command string, or None if the game cannot be mapped.
    """
    source = (game.get("Source") or "").lower()
    game_id = game.get("GameId", "")
    play_action = game.get("PlayAction") or {}

    # If PlayAction is a list, take the first entry
    if isinstance(play_action, list):
        play_action = play_action[0] if play_action else {}

    if source == "steam" and game_id:
        return f"steam://rungameid/{game_id}"

    if source == "epic" and game_id:
        return (
            f"com.epicgames.launcher://apps/{game_id}"
            f"?action=launch&silent=true"
        )

    if source == "gog" or source == "gog galaxy":
        path = play_action.get("Path", "")
        if path:
            return f'"{path}"'

    # Battle.net
    if source in ("battle.net", "battlenet", "blizzard"):
        path = play_action.get("Path", "")
        if path:
            return f'"{path}"'

    # Emulated games
    if play_action.get("Type") == "Emulator" or play_action.get("EmulatorId"):
        emulator_path = play_action.get("EmulatorProfileId", "")
        rom_path = play_action.get("Path", "")
        arguments = play_action.get("Arguments", "")
        if emulator_path and rom_path:
            return f'"{emulator_path}" {arguments} "{rom_path}"'

    # Generic / manual — use direct exe path
    path = play_action.get("Path", "")
    if path:
        arguments = play_action.get("Arguments", "")
        if arguments:
            return f'"{path}" {arguments}'
        return f'"{path}"'

    # Try InstallDirectory as a last resort
    install_dir = game.get("InstallDirectory", "")
    if install_dir:
        logger.warning(
            "Game '%s' has no PlayAction, using InstallDirectory: %s",
            game.get("Name", "Unknown"),
            install_dir,
        )
        return None

    return None


def resolve_artwork(
    game: dict,
    playnite_files_path: Path,
    artwork_dest_dir: Path,
    app_id: str,
) -> Optional[str]:
    """Resolve and copy cover art for a game.

    Returns the absolute path to the copied artwork, or None.
    """
    cover_ref = game.get("CoverImage", "")
    if not cover_ref:
        cover_ref = game.get("BackgroundImage", "")
    if not cover_ref:
        return None

    # Cover references can be absolute paths or relative to Playnite's files dir
    source_path = Path(cover_ref)
    if not source_path.is_absolute():
        source_path = playnite_files_path / cover_ref

    if not source_path.exists():
        logger.debug("Artwork not found: %s", source_path)
        return None

    artwork_dest_dir.mkdir(parents=True, exist_ok=True)
    ext = source_path.suffix or ".png"
    dest_path = artwork_dest_dir / f"{app_id}{ext}"

    try:
        shutil.copy2(str(source_path), str(dest_path))
        return str(dest_path)
    except OSError as e:
        logger.warning("Failed to copy artwork for '%s': %s", game.get("Name"), e)
        return None


def read_playnite_library(library_path: Path) -> list[dict]:
    """Read all game entries from the Playnite library directory."""
    games = []
    if not library_path.exists():
        logger.error("Playnite library path does not exist: %s", library_path)
        return games

    for game_file in library_path.glob("*.json"):
        try:
            with open(game_file, "r", encoding="utf-8") as f:
                game = json.load(f)
                games.append(game)
        except (json.JSONDecodeError, OSError) as e:
            logger.warning("Failed to read %s: %s", game_file, e)

    logger.info("Found %d games in Playnite library", len(games))
    return games


def read_existing_apollo_apps(apps_json_path: Path) -> dict:
    """Read existing Apollo apps.json, returning a dict keyed by app name."""
    if not apps_json_path.exists():
        return {}

    try:
        with open(apps_json_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, dict) and "apps" in data:
                apps_list = data["apps"]
            elif isinstance(data, list):
                apps_list = data
            else:
                return {}

            # Index by a sync marker so we can identify our entries
            result = {}
            for app in apps_list:
                sync_id = app.get("_playnite_sync_id")
                if sync_id:
                    result[sync_id] = app
            return result
    except (json.JSONDecodeError, OSError) as e:
        logger.warning("Failed to read existing apps.json: %s", e)
        return {}


def load_full_apps_json(apps_json_path: Path) -> list:
    """Load the full apps list from Apollo's apps.json."""
    if not apps_json_path.exists():
        return []

    try:
        with open(apps_json_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, dict) and "apps" in data:
                return data["apps"]
            elif isinstance(data, list):
                return data
            return []
    except (json.JSONDecodeError, OSError):
        return []


def sync(config: dict, dry_run: bool = False) -> None:
    """Perform a full sync from Playnite library to Apollo apps.json."""
    playnite_path = expand_path(config["playnite_library_path"])
    playnite_files_path = expand_path(config["playnite_files_path"])
    apollo_apps_path = expand_path(config["apollo_apps_json_path"])
    artwork_dir = expand_path(config["apollo_artwork_dir"])

    # Read Playnite library
    games = read_playnite_library(playnite_path)

    # Load existing apps.json (preserving non-synced entries)
    all_apps = load_full_apps_json(apollo_apps_path)
    manual_apps = [app for app in all_apps if "_playnite_sync_id" not in app]
    existing_synced = {
        app["_playnite_sync_id"]: app
        for app in all_apps
        if "_playnite_sync_id" in app
    }

    synced_apps = {}
    stats = {"added": 0, "updated": 0, "removed": 0, "skipped": 0}

    for game in games:
        name = game.get("Name", "Unknown")
        game_id = game.get("GameId") or game.get("Id", "")
        source = game.get("Source") or "unknown"
        is_installed = game.get("IsInstalled", False)

        if not is_installed:
            logger.debug("Skipping uninstalled game: %s", name)
            stats["skipped"] += 1
            continue

        app_id = generate_app_id(source, game_id)
        launch_cmd = build_launch_command(game)

        if not launch_cmd:
            logger.warning("No launch command for '%s' (source=%s), skipping", name, source)
            stats["skipped"] += 1
            continue

        image_path = resolve_artwork(game, playnite_files_path, artwork_dir, app_id)

        apollo_entry = {
            "name": name,
            "output": "",
            "cmd": "",
            "detached": [launch_cmd],
            "image-path": image_path or "",
            "auto-detach": "true",
            "wait-all": "true",
            "exit-timeout": "5",
            "_playnite_sync_id": app_id,
            "_playnite_source": source,
            "_playnite_game_id": game_id,
        }

        if app_id in existing_synced:
            if existing_synced[app_id] != apollo_entry:
                stats["updated"] += 1
                logger.info("Updated: %s", name)
            # else: unchanged
        else:
            stats["added"] += 1
            logger.info("Added: %s (%s)", name, source)

        synced_apps[app_id] = apollo_entry

    # Detect removed games
    for old_id in existing_synced:
        if old_id not in synced_apps:
            stats["removed"] += 1
            old_name = existing_synced[old_id].get("name", "Unknown")
            logger.info("Removed: %s (no longer installed)", old_name)

    # Combine manual apps + synced apps
    final_apps = manual_apps + list(synced_apps.values())

    logger.info(
        "Sync complete: %d added, %d updated, %d removed, %d skipped",
        stats["added"],
        stats["updated"],
        stats["removed"],
        stats["skipped"],
    )

    if dry_run:
        logger.info("Dry run — no files written")
        print(json.dumps({"apps": final_apps}, indent=2))
        return

    # Write apps.json
    apollo_apps_path.parent.mkdir(parents=True, exist_ok=True)
    with open(apollo_apps_path, "w", encoding="utf-8") as f:
        json.dump({"apps": final_apps}, f, indent=2)
    logger.info("Wrote %d apps to %s", len(final_apps), apollo_apps_path)

    # Optionally restart Apollo
    if config.get("restart_apollo_on_sync"):
        service_name = config.get("apollo_service_name", "Apollo")
        try:
            logger.info("Restarting Apollo service: %s", service_name)
            subprocess.run(
                ["net", "stop", service_name],
                capture_output=True,
                timeout=15,
            )
            time.sleep(2)
            subprocess.run(
                ["net", "start", service_name],
                capture_output=True,
                timeout=15,
            )
            logger.info("Apollo service restarted")
        except Exception as e:
            logger.warning("Failed to restart Apollo: %s", e)


def watch_mode(config: dict) -> None:
    """Watch the Playnite library for changes and re-sync automatically."""
    try:
        from watchdog.observers import Observer
        from watchdog.events import FileSystemEventHandler
    except ImportError:
        logger.error("watchdog package required for watch mode: pip install watchdog")
        sys.exit(1)

    playnite_path = expand_path(config["playnite_library_path"])

    class SyncHandler(FileSystemEventHandler):
        def __init__(self):
            self._last_sync = 0
            self._debounce_seconds = 5

        def on_any_event(self, event):
            now = time.time()
            if now - self._last_sync < self._debounce_seconds:
                return
            self._last_sync = now
            logger.info("Detected library change, re-syncing...")
            try:
                sync(config)
            except Exception as e:
                logger.error("Sync failed: %s", e)

    observer = Observer()
    observer.schedule(SyncHandler(), str(playnite_path), recursive=True)
    observer.start()
    logger.info("Watching %s for changes... (Ctrl+C to stop)", playnite_path)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
    observer.join()


def main():
    parser = argparse.ArgumentParser(
        description="Sync Playnite game library to Apollo's apps.json"
    )
    parser.add_argument(
        "--config",
        default="config.json",
        help="Path to configuration file (default: config.json)",
    )
    parser.add_argument(
        "--watch",
        action="store_true",
        help="Watch Playnite library for changes and auto-sync",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be synced without writing files",
    )
    args = parser.parse_args()

    config_path = Path(args.config)
    if not config_path.exists():
        logger.error("Config file not found: %s", config_path)
        sys.exit(1)

    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    if args.watch:
        # Do an initial sync, then watch for changes
        sync(config, dry_run=args.dry_run)
        if not args.dry_run:
            watch_mode(config)
    else:
        sync(config, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
