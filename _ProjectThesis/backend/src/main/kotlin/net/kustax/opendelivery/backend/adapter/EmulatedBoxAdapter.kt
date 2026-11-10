package net.kustax.opendelivery.backend.adapter

import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.port.FoodOrderEventListener

class EmulatedBoxAdapter(listener: FoodOrderEventListener, stepMillis: Long) :
    EmulatedFoodPlatformAdapter(ExternalPlatform.BOX, "/food-platforms/box.json", "/food-platforms/box-catalog.json", listener, stepMillis)
