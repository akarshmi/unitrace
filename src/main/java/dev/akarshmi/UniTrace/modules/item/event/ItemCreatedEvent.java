package dev.akarshmi.UniTrace.modules.item.event;

import java.util.UUID;

/** Published by ItemServiceImpl after an item is saved; triggers immediate embedding after commit. */
public record ItemCreatedEvent(UUID itemId) {
}
