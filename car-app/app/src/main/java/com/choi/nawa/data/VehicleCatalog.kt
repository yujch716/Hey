package com.choi.nawa.data

data class CarModel(val brand: String, val name: String, val body: BodyType)

data class CarColor(val name: String, val hex: String)

/** Main Hyundai / Kia / Genesis models for the registration dropdown. The body type is a suggestion. */
object VehicleCatalog {
    val models: List<CarModel> = listOf(
        "현대" to listOf(
            "아반떼" to BodyType.SEDAN, "쏘나타" to BodyType.SEDAN, "그랜저" to BodyType.SEDAN, "아이오닉 6" to BodyType.SEDAN,
            "캐스퍼" to BodyType.SUV, "코나" to BodyType.SUV, "투싼" to BodyType.SUV, "싼타페" to BodyType.SUV,
            "팰리세이드" to BodyType.SUV, "아이오닉 5" to BodyType.SUV, "아이오닉 9" to BodyType.SUV, "스타리아" to BodyType.SUV,
        ),
        "기아" to listOf(
            "K5" to BodyType.SEDAN, "K8" to BodyType.SEDAN, "모닝" to BodyType.SEDAN, "레이" to BodyType.SUV,
            "셀토스" to BodyType.SUV, "스포티지" to BodyType.SUV, "쏘렌토" to BodyType.SUV, "카니발" to BodyType.SUV,
            "EV3" to BodyType.SUV, "EV6" to BodyType.SUV, "EV9" to BodyType.SUV,
        ),
        "제네시스" to listOf(
            "G70" to BodyType.SEDAN, "G80" to BodyType.SEDAN, "G90" to BodyType.SEDAN,
            "GV60" to BodyType.SUV, "GV70" to BodyType.SUV, "GV80" to BodyType.SUV,
        ),
    ).flatMap { (brand, list) -> list.map { (name, body) -> CarModel(brand, name, body) } }

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
