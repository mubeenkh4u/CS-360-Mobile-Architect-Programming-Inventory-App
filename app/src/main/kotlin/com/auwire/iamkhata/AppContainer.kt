package com.auwire.iamkhata

import android.content.Context
import com.auwire.iamkhata.core.data.AndroidKeystoreIntegritySigner
import com.auwire.iamkhata.core.data.DatasetRepository
import com.auwire.iamkhata.core.data.InventoryRepository
import com.auwire.iamkhata.core.data.RoomDatasetRepository
import com.auwire.iamkhata.core.data.RoomInventoryRepository
import com.auwire.iamkhata.core.database.IamDatabase
import com.auwire.iamkhata.core.model.InventoryPolicy

/**
 * Application composition root.
 *
 * Repositories share one Room database and one Keystore-backed signer so stock,
 * Khata and audit writes participate in the same local integrity boundary.
 */
class AppContainer(
    context: Context,
    inventoryPolicy: InventoryPolicy,
) {
    private val database = IamDatabase.create(context)
    private val signer = AndroidKeystoreIntegritySigner()

    val datasetRepository: DatasetRepository = RoomDatasetRepository(
        database = database,
        signer = signer,
    )

    val inventoryRepository: InventoryRepository = RoomInventoryRepository(
        database = database,
        signer = signer,
        policy = inventoryPolicy,
    )
}
