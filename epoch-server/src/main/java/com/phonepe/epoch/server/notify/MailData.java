package com.phonepe.epoch.server.notify;

import java.util.List;

/**
 *
 */
public record MailData(List<String> emailIds, String subject, String body) {
}
