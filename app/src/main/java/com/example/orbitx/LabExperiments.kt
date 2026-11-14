package com.example.orbitx

/** Where an improvement sits in the discover → test → deploy → recheck loop. */
internal val ExperimentStages = listOf("발견", "시험", "배포", "재확인")

/**
 * One X-LAB experiment as a bundle of settings. Every experiment plugs into the same two shared PLAY
 * tools (the efficiency league for numbers, a drive-report question for the driver's reason) and adds
 * at most one play of its own, so a new experiment needs no new PLAY screen. In operation this bundle
 * would come from the server instead of the app.
 */
internal data class LabExperiment(
    val id: String,
    val number: Int,
    val title: String,
    /** The inconvenience or waste this experiment starts from. */
    val problem: String,
    /** Index into [ExperimentStages]. */
    val stage: Int,
    /** What the efficiency league compares for this experiment, or null when it is not a numbers question. */
    val leagueMetric: String?,
    /** Asked once, in rotation, in the drive report right after arrival. */
    val driveQuestion: String,
    val answers: List<String>,
    /** The one play this experiment adds on top of the shared ones. */
    val extraPlay: String,
    val extraReady: Boolean,
)

internal val LabExperiments = listOf(
    LabExperiment(
        "cabin-climate", 1, "착석 연동 공조",
        "혼자 타도 전 좌석 공조가 켜져 에너지가 새요.",
        stage = 1,
        leagueMetric = "착석 연동 공조 사용/미사용 차량 전비",
        driveQuestion = "오늘 공조가 쾌적했나요?",
        answers = listOf("쾌적했어요", "불편했어요", "모르겠어요"),
        extraPlay = "7일 써보기 장기 미션", extraReady = true,
    ),
    LabExperiment(
        "door-lock", 2, "도어 잠금 버튼",
        "OTA로 주행 정보 화면에 잠금 버튼이 생겼지만 찾기 쉬운지 몰라요.",
        stage = 3,
        leagueMetric = null,
        driveQuestion = "도어 잠금 버튼을 바로 찾았나요?",
        answers = listOf("찾았어요", "헤맸어요", "안 썼어요"),
        extraPlay = "실제 도어로 OTA 체험 평가", extraReady = true,
    ),
    LabExperiment(
        "charging-stop", 3, "충전 경유 알림",
        "배터리가 부족해도 주행 중에는 충전소를 찾기 어려워요.",
        stage = 1,
        leagueMetric = "도착 배터리와 충전 구간",
        driveQuestion = "주행 중 충전 제안이 도움됐나요?",
        answers = listOf("도움됐어요", "필요 없어요", "못 받았어요"),
        extraPlay = "주행 중 내비 위 충전 알림", extraReady = true,
    ),
    LabExperiment(
        "vent-direction", 4, "송풍구 방향 바로 조절",
        "숨은 송풍구 방향을 화면 메뉴 여러 단계에서만 바꿀 수 있어요.",
        stage = 0,
        leagueMetric = null,
        driveQuestion = "오늘 바람 방향을 바꾸다 불편했나요?",
        answers = listOf("불편했어요", "괜찮았어요", "안 바꿨어요"),
        extraPlay = "원터치 바람 방향 A/B", extraReady = false,
    ),
    LabExperiment(
        "driving-info", 5, "주행 정보 역할 나누기",
        "같은 정보가 슬림 디스플레이, HUD, 드라이빙 뷰에 겹쳐 보여요.",
        stage = 0,
        leagueMetric = null,
        driveQuestion = "같은 정보가 여러 화면에 보여 헷갈렸나요?",
        answers = listOf("헷갈렸어요", "괜찮았어요", "모르겠어요"),
        extraPlay = "화면별로 볼 정보 고르기", extraReady = false,
    ),
    LabExperiment(
        "gleo-voice", 6, "Gleo 음성 명령",
        "‘에어컨 높여줘, 더워’ 같은 일상 표현을 못 알아들을 때가 있어요.",
        stage = 0,
        leagueMetric = null,
        driveQuestion = "Gleo가 못 알아들은 말이 있었나요?",
        answers = listOf("있었어요", "없었어요", "안 썼어요"),
        extraPlay = "못 알아들은 표현 모으기", extraReady = false,
    ),
)

internal fun labExperiment(id: String?): LabExperiment? = LabExperiments.firstOrNull { it.id == id }
