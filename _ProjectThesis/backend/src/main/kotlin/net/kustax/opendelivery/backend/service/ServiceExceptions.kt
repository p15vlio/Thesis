package net.kustax.opendelivery.backend.service

class NotFoundException(message: String) : Exception(message)
class ConflictException(message: String) : Exception(message)
class ForbiddenException(message: String) : Exception(message)
// An external dependency (SMTP server, GEMH API) rejected or failed the call; the message is safe to show.
class ExternalServiceException(message: String) : Exception(message)
