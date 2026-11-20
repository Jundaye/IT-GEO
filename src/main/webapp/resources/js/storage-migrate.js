/* 브라우저 저장 키 이름 바꾸기 (2026-11-20, 서비스 이름 변경)
   옛 키 값을 itgeo.* 로 한 번 옮기고 옛 키는 지운다 → 테마 · 패널 폭 · 지도 종류 · 저장한 아이디가 그대로 유지된다.
   head.jspf 가 테마를 읽기 전에 가장 먼저 불러온다. 옮길 것이 없으면 아무 일도 하지 않는다. */
(function () {
  var OLD = ['wf.', 'well' + 'fair.'];        // 옛 접두어 (둘째는 로그인 아이디 저장 키)
  function move(get) {
    try {
      var store = get(), keys = [], i;
      for (i = 0; i < store.length; i++) keys.push(store.key(i));
      keys.forEach(function (k) {
        OLD.forEach(function (p) {
          if (k.indexOf(p) !== 0) return;
          var nk = 'itgeo.' + k.slice(p.length);
          if (store.getItem(nk) === null) store.setItem(nk, store.getItem(k));
          store.removeItem(k);
        });
      });
    } catch (e) {}                             // 저장소가 막혀 있어도 화면은 그대로
  }
  window.migrateStorageKeys = function () {
    move(function () { return window.localStorage; });
    move(function () { return window.sessionStorage; });
  };
  window.migrateStorageKeys();
})();
