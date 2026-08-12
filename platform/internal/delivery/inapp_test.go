package delivery

import (
	"context"
	"testing"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestSendNotification_InAppSavesToDB(t *testing.T) {
	svc := &service{testPool: &testutil.MockPool{}}
	notif := Notification{
		ID:          "n1",
		TenantID:    "T01",
		WorkspaceID: "WS01",
		UserID:      "U01",
		Type:        NotificationScoreDrop,
		Channel:     ChannelInApp,
		Title:       "Skor düştü",
		Body:        "Acme skoru 10 puan düştü",
		Data:        map[string]interface{}{"brand": "Acme"},
		CreatedAt:   time.Now(),
	}
	if err := svc.SendNotification(notif); err != nil {
		t.Fatalf("in-app bildirim kaydedilemedi: %v", err)
	}
}

func TestSendNotification_InAppCallsDB(t *testing.T) {
	// Mock Exec'in çağrıldığını doğrula — kanal artık pasif değil, DB'ye yazıyor.
	mp := &testutil.MockPool{}
	called := false
	mp.ExecFunc = func(ctx context.Context, sql string, args ...any) (dbiface.CommandResult, error) {
		called = true
		return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
	}
	svc := &service{testPool: mp}
	notif := Notification{ID: "n2", TenantID: "T01", WorkspaceID: "WS01", Channel: ChannelInApp}
	if err := svc.SendNotification(notif); err != nil {
		t.Fatalf("beklenmeyen hata: %v", err)
	}
	if !called {
		t.Error("in-app INSERT çağrısı yapılmalı (kanal pasif olmamalı)")
	}
}

func TestListInAppNotifications_Empty(t *testing.T) {
	svc := &service{testPool: &testutil.MockPool{}}
	notifs, err := svc.ListInAppNotifications(context.Background(), "T01", "WS01", true, 10)
	if err != nil {
		t.Fatalf("liste hatası: %v", err)
	}
	if notifs == nil {
		t.Fatal("boş liste nil olmamalı (boş slice dönmeli)")
	}
	if len(notifs) != 0 {
		t.Errorf("beklenen 0 bildirim, gerçek %d", len(notifs))
	}
}

func TestListInAppNotifications_WithRows(t *testing.T) {
	now := time.Now().UTC().Format(time.RFC3339)
	mp := &testutil.MockPool{}
	mp.QueryFunc = func(ctx context.Context, sql string, args ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"n1", "U01", "score_drop", "Skor düştü", "Acme düştü", []byte("{}"), false, now},
		}), nil
	}
	svc := &service{testPool: mp}
	notifs, err := svc.ListInAppNotifications(context.Background(), "T01", "WS01", false, 10)
	if err != nil {
		t.Fatalf("liste hatası: %v", err)
	}
	if len(notifs) != 1 {
		t.Fatalf("beklenen 1 bildirim, gerçek %d", len(notifs))
	}
	if notifs[0].Title != "Skor düştü" {
		t.Errorf("başlık yanlış: %s", notifs[0].Title)
	}
	if notifs[0].IsRead {
		t.Error("is_read false olmalı")
	}
}

func TestMarkInAppNotificationRead(t *testing.T) {
	svc := &service{testPool: &testutil.MockPool{}}
	if err := svc.MarkInAppNotificationRead(context.Background(), "T01", "n1"); err != nil {
		t.Fatalf("okundu işaretlenemedi: %v", err)
	}
}
