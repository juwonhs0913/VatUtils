/**
 * VATRadar — 관심 관제소 접속 감지 (F4)
 *
 * 앱 단독(WorkManager)으로는 Doze 제약 때문에 최소 15분 간격이 한계입니다.
 * 이 함수는 1분마다 VATSIM 데이터 피드를 확인하고, 새로 접속한 관제소를
 * 해당 토픽 구독자에게 FCM data 메시지로 즉시 전송합니다.
 *
 * 토픽 규칙: 콜사인 접두사를 그대로 씁니다. 예) RKSI → 토픽 "cs_RKSI"
 * 앱은 사용자가 등록한 키워드마다 해당 토픽을 구독합니다.
 *
 * 배포:
 *   cd server && npm install && firebase deploy --only functions
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");
const { getFirestore } = require("firebase-admin/firestore");

initializeApp();

const VATSIM_DATA_URL = "https://data.vatsim.net/v3/vatsim-data.json";
const OBS_FACILITY = 0;
const STATE_DOC = "vatradar_state/online_controllers";

exports.watchControllers = onSchedule(
  {
    schedule: "every 1 minutes",
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async () => {
    const db = getFirestore();

    const response = await fetch(VATSIM_DATA_URL);
    if (!response.ok) {
      console.error(`VATSIM 피드 응답 오류: ${response.status}`);
      return;
    }
    const data = await response.json();

    // OBS(관찰자)는 실제 관제가 아니므로 제외합니다.
    const online = (data.controllers || [])
      .filter((c) => c.facility !== OBS_FACILITY)
      .map((c) => String(c.callsign || "").toUpperCase())
      .filter(Boolean);

    const snapshot = await db.doc(STATE_DOC).get();
    const previous = new Set(snapshot.exists ? snapshot.data().callsigns || [] : []);

    // 이번 주기에 새로 뜬 것만 알립니다.
    // 접속이 유지되는 동안 매분 울리면 안 되니까요.
    const newlyOnline = online.filter((cs) => !previous.has(cs));

    await db.doc(STATE_DOC).set({
      callsigns: online,
      updatedAt: Date.now(),
    });

    if (newlyOnline.length === 0) return;

    const messaging = getMessaging();

    // 콜사인 하나가 여러 접두사에 해당할 수 있습니다.
    // RKSI_TWR 이면 "RKSI", "RKSI_TWR" 토픽 모두에 보냅니다.
    const sends = [];
    for (const callsign of newlyOnline) {
      const topics = new Set([callsign, callsign.split("_")[0]]);
      for (const topic of topics) {
        if (!topic) continue;
        sends.push(
          messaging
            .send({
              topic: `cs_${topic}`,
              data: { callsigns: callsign },
              android: { priority: "high" },
            })
            .catch((err) => {
              // 구독자가 없는 토픽은 정상적인 상황입니다.
              if (err.code !== "messaging/invalid-argument") {
                console.warn(`토픽 cs_${topic} 전송 실패: ${err.message}`);
              }
            })
        );
      }
    }

    await Promise.all(sends);
    console.log(`새로 접속한 관제소 ${newlyOnline.length}곳 알림 전송`);
  }
);
