#!/usr/bin/env bash

export JCHAT_HOME="/home/ykx/jchat"
export JAVA_HOME="$JCHAT_HOME/.local/jdk/jdk-21.0.10+7"
export GRADLE_USER_HOME="$JCHAT_HOME/.local/gradle-home"

case ":$PATH:" in
    *":$JAVA_HOME/bin:"*) ;;
    *) export PATH="$JAVA_HOME/bin:$PATH" ;;
esac

case ":$PATH:" in
    *":$JCHAT_HOME/.local/gradle-dist/gradle-8.12.1/bin:"*) ;;
    *) export PATH="$JCHAT_HOME/.local/gradle-dist/gradle-8.12.1/bin:$PATH" ;;
esac
