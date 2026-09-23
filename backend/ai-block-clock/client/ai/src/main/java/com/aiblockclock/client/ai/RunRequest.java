package com.aiblockclock.client.ai;

public record RunRequest(String workflow, String input, String comment, boolean mock) {
}
