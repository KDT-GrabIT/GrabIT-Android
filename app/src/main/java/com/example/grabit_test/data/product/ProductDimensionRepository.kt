package com.example.grabit_test.data.product

import android.content.Context
import android.util.Log
import com.example.grabit_test.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStreamReader

private const val TAG = "ProductDimensionRepo"
private const val ASSET_FILE = "product_dimensions.json"

/**
 * product_dimensions.json(assets) 을 DB 에 한 번 로드.
 * 상품 이미지 폴더의 extract_meta_dimensions.py 실행 후 생성한 JSON 을
 * app/src/main/assets/product_dimensions.json 에 두면 앱 실행 시 비어 있을 때만 삽입.
 */
object ProductDimensionRepository {

    suspend fun seedFromAssetsIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.productDimensionDao()
        if (dao.getAll().isNotEmpty()) return@withContext

        try {
            context.assets.open(ASSET_FILE).use { stream ->
                val json = InputStreamReader(stream, Charsets.UTF_8).readText()
                val arr = JSONArray(json)
                val list = mutableListOf<ProductDimension>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val barcd = obj.optString("barcd", "").takeIf { it.isNotBlank() } ?: continue
                    list.add(
                        ProductDimension(
                            barcd = barcd,
                            itemNo = obj.optString("item_no", "").takeIf { it.isNotBlank() },
                            imgProdNm = obj.optString("img_prod_nm", "").takeIf { it.isNotBlank() },
                            widthCm = obj.optDouble("width_cm").takeIf { !obj.isNull("width_cm") }?.toFloat(),
                            lengthCm = obj.optDouble("length_cm").takeIf { !obj.isNull("length_cm") }?.toFloat(),
                            heightCm = obj.optDouble("height_cm").takeIf { !obj.isNull("height_cm") }?.toFloat()
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    dao.insertAll(list)
                    Log.i(TAG, "상품 치수 ${list.size}건 DB 적재 완료")
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("assets/$ASSET_FILE") == true) {
                Log.w(TAG, "product_dimensions.json 없음. extract_meta_dimensions.py 실행 후 assets 에 복사하세요.")
            } else {
                Log.e(TAG, "상품 치수 시드 실패", e)
            }
        }
    }
}
