package net.kustax.opendelivery.backend.adapter

import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.port.FoodOrderEventListener

class EmulatedEfoodAdapter(listener: FoodOrderEventListener, stepMillis: Long) :
    EmulatedFoodPlatformAdapter(ExternalPlatform.EFOOD, "/food-platforms/efood.json", "/food-platforms/efood-catalog.json", listener, stepMillis)
