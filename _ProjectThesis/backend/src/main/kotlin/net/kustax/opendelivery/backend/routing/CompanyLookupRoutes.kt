package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.NotFoundException
import net.kustax.opendelivery.backend.service.VAT_ID_REGEX
import net.kustax.opendelivery.data.response.CompanyLookupResponse
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.domain.port.CompanyRegistry

fun Route.companyLookupRoutes(companyRegistry: CompanyRegistry) {
    get("/company-lookup") {
        if (call.userRole() != UserRole.PLATFORM_ADMIN) throw ForbiddenException("Platform admin access required")
        val vatId = call.request.queryParameters["vatId"]?.trim().orEmpty()
        require(VAT_ID_REGEX.matches(vatId)) { "VAT ID must be exactly 9 digits" }
        val company = companyRegistry.findByVatId(vatId)
            ?: throw NotFoundException("No company found in GEMH for VAT ID $vatId")
        call.respond(
            HttpStatusCode.OK,
            CompanyLookupResponse(
                vatId = company.vatId,
                gemhNumber = company.gemhNumber,
                companyName = company.companyName,
                distinctiveTitle = company.distinctiveTitle,
                legalForm = company.legalForm,
                companyActivity = company.companyActivity,
                streetName = company.streetName,
                streetNo = company.streetNo,
                postalCode = company.postalCode,
                city = company.city,
                website = company.website,
                statusDescription = company.statusDescription,
                isActive = company.isActive
            )
        )
    }
}
