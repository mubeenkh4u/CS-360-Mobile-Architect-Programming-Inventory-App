package com.auwire.iamkhata

import android.app.Application
import com.auwire.iamkhata.core.model.InventoryPolicy

/** Owns application-scoped dependencies and runtime business policy. */
class IamKhataApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            context = this,
            inventoryPolicy = InventoryPolicy(
                tentativeStockEnabled = BuildConfig.FEATURE_TENTATIVE_STOCK,
                allowNegativeCommittedStock = BuildConfig.ALLOW_NEGATIVE_COMMITTED_STOCK,
            ),
        )
    }
}
