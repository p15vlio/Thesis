package net.kustax.opendelivery.backend.adapter

import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.port.FoodOrderEventListener

class EmulatedWoltAdapter(listener: FoodOrderEventListener, stepMillis: Long) :
    EmulatedFoodPlatformAdapter(ExternalPlatform.WOLT, "/food-platforms/wolt.json", "/food-platforms/wolt-catalog.json", listener, stepMillis)
