package com.choi.nawa.data

data class CarModel(val name: String, val body: BodyType)

data class CarColor(val name: String, val hex: String)

/** Main Hyundai models for the registration dropdown. */
object VehicleCatalog {
    val models: List<CarModel> = listOf(
        // Sedan
        CarModel("아반떼", BodyType.SEDAN),
        CarModel("쏘나타", BodyType.SEDAN),
        CarModel("그랜저", BodyType.SEDAN),
        CarModel("아이오닉 6", BodyType.SEDAN),

        // SUV
        CarModel("캐스퍼", BodyType.SUV),
        CarModel("베뉴", BodyType.SUV),
        CarModel("코나", BodyType.SUV),
        CarModel("투싼", BodyType.SUV),
        CarModel("싼타페", BodyType.SUV),
        CarModel("팰리세이드", BodyType.SUV),
        CarModel("아이오닉 5", BodyType.SUV),
        CarModel("아이오닉 9", BodyType.SUV),
        CarModel("넥쏘", BodyType.SUV),

        // MPV
        CarModel("스타리아", BodyType.SUV),

        // Commercial
        CarModel("포터", BodyType.TRUCK),
        CarModel("ST1", BodyType.TRUCK),
    )

    val colors: List<CarColor> = listOf(
        CarColor("흰색", "#f4f5f7"),
        CarColor("검은색", "#1c1f24"),
        CarColor("회색", "#7d838c"),
        CarColor("은색", "#c3c8cf"),
        CarColor("남색", "#1f2f55"),
        CarColor("파란색", "#2d5bb8"),
        CarColor("빨간색", "#b3262e"),
        CarColor("녹색", "#2f5d46"),
        CarColor("베이지", "#cdbb9b"),
    )
}
