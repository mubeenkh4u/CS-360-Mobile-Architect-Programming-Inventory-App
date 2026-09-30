package com.auwire.iamkhata

import android.content.Context
import com.auwire.iamkhata.core.data.AndroidKeystoreIntegritySigner
import com.auwire.iamkhata.core.data.DatasetRepository
import com.auwire.iamkhata.core.data.RoomDatasetRepository
import com.auwire.iamkhata.core.database.IamDatabase

/**
 * Small composition root.
 *
 * Dependencies are constructed once and exposed through interfaces. This keeps
 * feature code framework-independent and makes future replacement/testing easy.
 */
class AppContainer(context: Context) {
    private val database = IamDatabase.create(context)

    val datasetRepository: DatasetRepository = RoomDatasetRepository(
        database = database,
        signer = AndroidKeystoreIntegritySigner(),
    )
}
