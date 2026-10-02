# Ocage

The RuneLite plugin for the **Ocage** clan. It reports your boss kills and notable drops to the clan's Discord bot, so they count for clan bingos and boss events and appear in the clan's drop log on [ocage.cc](https://www.ocage.cc).

It's only useful to Ocage members: it does nothing until you link it to your Discord account.

## Getting started

1. Install **Ocage** from the Plugin Hub, and make sure RuneLite's **Loot Tracker** plugin is on (it's on by default).
2. In the Ocage Discord, run `/plugin link`. The bot replies with a one-time code.
3. Open the **Ocage** side panel in RuneLite (the OCAGE icon), paste the code and click **Link**, while logged in to the account you want to link.

Each game account is linked separately. To stop, click **Unlink** in the panel or turn the plugin off.

## What it does

- **Boss events:** while a clan boss event is running, each kill of a tracked boss counts toward your total. The side panel shows the KC you've gained for each boss and your unique drops.
- **Bingo:** during a bingo you're on a team for, an overlay shows the event, your team, the event password and the time. A drop on the bingo's item list is screenshotted and submitted for a moderator to review. Nothing is approved automatically. The side panel shows your team's rank and points.
- **Drop log:** valuable drops, raid and clue items, pets and new collection-log items go to the clan's drop log.
- **Combat Achievements:** reaching a new Combat Achievements tier is announced in the clan's Discord. Tiers you already have when you install the plugin are recorded but not announced.
- **Chat messages** tell you when a kill or drop counts or you reach a new Combat Achievements tier, and when your bingo team's points or rank change.

Outside a clan event, kills aren't sent. Drops are only sent when the clan's rules ask for them: collection-log items, items the moderators listed, and anything above a value set by the clan.

## Settings

| Setting | What it does |
|---|---|
| Show counted drops | A chat message when a kill or drop counts for an event, or you reach a new Combat Achievements tier |
| Show bingo overlay | The event overlay during a bingo. Screenshots of bingo drops always include it |
| Screenshot bingo drops | Off: bingo drops are still submitted, without a picture |
| Bingo standing updates | A chat message when your team's points or rank change |
| Screenshot chat | What bingo screenshots hide: private messages (the default), the whole chat box, or nothing |

## What it sends, and where

Everything goes to the Ocage clan's server at `api.ocage.cc`, which is run by the clan, not by RuneLite:

- your in-game name and an anonymous account id (RuneLite's account hash), so drops are credited to the right account;
- the boss and kill count of kills while an event is running;
- the drops, pets and collection-log items described above, with their value;
- your Combat Achievements tier;
- during a bingo, screenshots of your game screen for drops on the bingo's item list (can be turned off);
- like any website you connect to, your IP address.

Clan moderators can see what you've sent; the drop log is public on ocage.cc. Reports that can't be sent are kept in `.runelite/ocage/` until they can.

## Help

Ask in the Ocage Discord, or see [ocage.cc](https://www.ocage.cc).
