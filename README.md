# videoarchivebot



## Prerequisites

A computer running Linux (or Mac OS (untested)), with at least ffmpeg and a recent JDK (recommended: OpenJDK 11) installed. youtube-dl is also recommended for vastly improved video downloading capabilities.

## Quickstart Guide

1. Start the bot.
   Execute `./run.sh` in the root directory of the project.
2. After compiling the code, it should wait for user input. Type `list` for a list of commands, and (for some) descriptions of what they do.
3. Set up login information for Reddit. Set the following variables, replacing the placeholder values with your own:
   App ID: `app_id 123456789`
   App Secret: `app_secret ABCDEFABCDEFABCDEFABCDEF`
   Username: `reddit_username botuser`
   Password: `reddit_password botpw`
   Also set the bot provider to your main username
   `bot_provider the_real_farfetchd`
4. Try to log in: `connect`.
   It should show a message saying `login status: true`.
   If not, check your login data.
   (Next time starting, it will log you in automatically.)
5. Set up downloading. You can set download handlers for specific domains (in the data/dl directory) with the `dl_handler` command: `dl_handler youtube.com youtube-dl.sh`
   To remove a download handler, use `dl_handler_remove yourdomain.com`. To remove all download handlers, use `dl_handler_clear`
   In case no handler is registered for a domain, it will use the one from the `dl_handler_default` variable. By default, this is set to `default.sh` which just calls wget, so it's not suitable for most sites. Instead, it's recommended to set this to `youtube-dl.sh`, which supports a lot of sites out of the box that serve video. Since it uses youtube-dl to download video, make sure that's installed.
6. Set up a list of subreddits to watch for new content with the `watch` command (reverse this with `unwatch`, or `unwatchall` to remove all subs from the watch list)
   Example: `watch videos` - Start watching /r/videos for new content
   Disable watching for new content by setting `watch_enable 0` (enable again with `watch_enable 1`)
7. I guess that's it? Type `save` to save configuration (this will happen automatically on shutdown), and `quit` to shut down the bot.


## Folder structure

Everything that the bot uses is located in the data folder.

 - `data/archive`: The archived videos and post data.
 - `data/cfg`: Configuration files. Most of these are overwritten with the `save` command or when the bot shuts down, but you can set up custom commands in `autoexec.cfg` which gets loaded after everything else.
 - `data/dl`: Download handlers
 - `data/log`: Log files
 - `data/ul`: Upload handlers
 - `data/work`: Temporary files

## To do

 - a lot