package delivery

import (
	"testing"
)

// validSettings returns a NotificationSettings with valid defaults for testing.
func validSettings() *NotificationSettings {
	return &NotificationSettings{
		WorkspaceID:   "ws-test",
		EmailAddress:  "test@example.com",
		DigestEnabled: true,
		DigestDay:     "monday",
		DigestTime:    "09:00",
		DigestFormat:  "email",
		NotifyOnDrop:  true,
		DropThreshold: 10,
	}
}

func TestValidateSettings_Valid(t *testing.T) {
	s := validSettings()
	if err := ValidateSettings(s); err != nil {
		t.Errorf("valid settings should pass: %v", err)
	}
}

func TestValidateSettings_EmailRequired(t *testing.T) {
	s := validSettings()
	s.EmailAddress = ""
	err := ValidateSettings(s)
	if err == nil {
		t.Fatal("expected error for empty email")
	}
	if _, ok := err.(*validationError); !ok {
		t.Errorf("expected validationError type, got %T", err)
	}
}

func TestValidateSettings_InvalidDay(t *testing.T) {
	tests := []struct {
		day   string
		valid bool
	}{
		{"monday", true},
		{"tuesday", true},
		{"wednesday", true},
		{"thursday", true},
		{"friday", true},
		{"saturday", true},
		{"sunday", true},
		{"", false}, // empty is now required
		{"invalid", false},
		{"MONDAY", false}, // must be lowercase
		{"Monday", false}, // must be lowercase
		{"Pazartesi", false},
		{"monday ", false}, // trailing space
	}

	for _, tc := range tests {
		s := validSettings()
		s.DigestDay = tc.day
		err := ValidateSettings(s)
		if tc.valid && err != nil {
			t.Errorf("day %q should be valid, got: %v", tc.day, err)
		}
		if !tc.valid && err == nil {
			t.Errorf("day %q should be invalid, but passed", tc.day)
		}
	}
}

func TestValidateSettings_InvalidTime(t *testing.T) {
	tests := []struct {
		time  string
		valid bool
	}{
		{"09:00", true},
		{"23:59", true},
		{"00:00", true},
		{"12:30", true},
		{"", false},       // empty is now required
		{"9:00", false},   // missing leading zero
		{"24:00", false},  // hour out of range
		{"09:60", false},  // minute out of range
		{"09-00", false},  // wrong separator
		{"0900", false},   // missing colon
		{"abc:00", false}, // non-numeric hour
		{"09:ab", false},  // non-numeric minute
	}

	for _, tc := range tests {
		s := validSettings()
		s.DigestTime = tc.time
		err := ValidateSettings(s)
		if tc.valid && err != nil {
			t.Errorf("time %q should be valid, got: %v", tc.time, err)
		}
		if !tc.valid && err == nil {
			t.Errorf("time %q should be invalid, but passed", tc.time)
		}
	}
}

func TestValidateSettings_InvalidFormat(t *testing.T) {
	tests := []struct {
		format string
		valid  bool
	}{
		{"email", true},
		{"pdf", true},
		{"both", true},
		{"", false},      // empty is now required
		{"Email", false}, // case sensitive
		{"EMAIL", false},
		{"sms", false},
		{"email-pdf", false},
	}

	for _, tc := range tests {
		s := validSettings()
		s.DigestFormat = tc.format
		err := ValidateSettings(s)
		if tc.valid && err != nil {
			t.Errorf("format %q should be valid, got: %v", tc.format, err)
		}
		if !tc.valid && err == nil {
			t.Errorf("format %q should be invalid, but passed", tc.format)
		}
	}
}

func TestValidateSettings_ThresholdRange(t *testing.T) {
	tests := []struct {
		threshold int
		valid     bool
	}{
		{1, true},
		{10, true},
		{50, true},
		{100, true},
		{0, false},
		{-1, false},
		{101, false},
	}

	for _, tc := range tests {
		s := validSettings()
		s.DropThreshold = tc.threshold
		err := ValidateSettings(s)
		if tc.valid && err != nil {
			t.Errorf("threshold %d should be valid, got: %v", tc.threshold, err)
		}
		if !tc.valid && err == nil {
			t.Errorf("threshold %d should be invalid, but passed", tc.threshold)
		}
	}
}

func TestValidateSettings_MultipleErrors(t *testing.T) {
	// Only the first error is returned, so test each separately
	s := validSettings()
	s.EmailAddress = ""
	s.DigestDay = "invalid"
	s.DigestTime = "99:99"
	s.DigestFormat = "sms"
	s.DropThreshold = 200

	err := ValidateSettings(s)
	if err == nil {
		t.Fatal("expected at least one validation error")
	}
	if _, ok := err.(*validationError); !ok {
		t.Errorf("expected validationError type, got %T", err)
	}
	// Should be the email error since it's first
	if err.Error() != "e-posta adresi gerekli" {
		t.Errorf("expected email error first, got: %v", err)
	}
}

func TestUpdateSettings_CallsValidate(t *testing.T) {
	svc := &service{}
	s := validSettings()
	s.EmailAddress = "" // invalid

	err := svc.UpdateSettings(s)
	if err == nil {
		t.Fatal("expected validation error from UpdateSettings")
	}
	if _, ok := err.(*validationError); !ok {
		t.Errorf("expected validationError type, got %T", err)
	}

	// Now save valid settings
	s.EmailAddress = "test@example.com"
	err = svc.UpdateSettings(s)
	if err != nil {
		t.Fatalf("valid settings should save: %v", err)
	}

	// Verify it was stored
	got, err := svc.GetSettings("ws-test")
	if err != nil {
		t.Fatalf("GetSettings failed: %v", err)
	}
	if got.EmailAddress != "test@example.com" {
		t.Errorf("expected email test@example.com, got %s", got.EmailAddress)
	}
	if got.DigestDay != "monday" {
		t.Errorf("expected day monday, got %s", got.DigestDay)
	}
}

func TestGetSettings_Defaults(t *testing.T) {
	svc := &service{} // empty map

	settings, err := svc.GetSettings("non-existent")
	if err != nil {
		t.Fatalf("GetSettings for non-existent workspace should return defaults: %v", err)
	}
	if settings.WorkspaceID != "non-existent" {
		t.Errorf("expected workspace non-existent, got %s", settings.WorkspaceID)
	}
	if !settings.DigestEnabled {
		t.Error("default digest_enabled should be true")
	}
	if settings.DigestDay != "monday" {
		t.Errorf("default digest_day should be monday, got %s", settings.DigestDay)
	}
	if settings.DigestTime != "09:00" {
		t.Errorf("default digest_time should be 09:00, got %s", settings.DigestTime)
	}
	if settings.DropThreshold != 10 {
		t.Errorf("default drop_threshold should be 10, got %d", settings.DropThreshold)
	}
}
