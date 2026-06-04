/*
 * Web Push Service Worker (푸시 알림 0단계).
 * 페이로드는 서버 PushSendService 가 보내는 {title, body, url} JSON.
 * 알림 클릭 시 url 로 이동 — 이미 열린 탭이 있으면 포커스, 없으면 새 창.
 */
self.addEventListener('push', function (event) {
  var data = {};
  try { data = event.data.json(); } catch (e) { /* 페이로드 없는 푸시 — 기본값 표시 */ }
  event.waitUntil(self.registration.showNotification(data.title || '가계부', {
    body: data.body || '',
    data: { url: data.url || '/web/home' }
  }));
});

self.addEventListener('notificationclick', function (event) {
  event.notification.close();
  var url = (event.notification.data && event.notification.data.url) || '/web/home';
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (windows) {
      for (var i = 0; i < windows.length; i++) {
        if ('focus' in windows[i]) { windows[i].navigate(url); return windows[i].focus(); }
      }
      return clients.openWindow(url);
    })
  );
});
