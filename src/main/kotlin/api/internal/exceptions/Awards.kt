package org.rdm.online.api.internal.exceptions

/** Business-rule failures (missing category, locked, over the limit). */
class AwardsRuleViolation(message: String) : RuntimeException(message)