#!/usr/bin/env bash

youtube-dl -o "%(title)s.%(ext)s" "$1"