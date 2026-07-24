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
		{"", false},
		{"invalid", false},
		{"MONDAY", false},
		{"Monday", false},
		{"Pazartesi", false},
		{"monday ", false},
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
		{"", false},
		{"9:00", false},
		{"24:00", false},
		{"09:60", false},
		{"09-00", false},
		{"0900", false},
		{"abc:00", false},
		{"09:ab", false},
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
		{"", false},
		{"Email", false},
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
	if err.Error() != "e-posta adresi gerekli" {
		t.Errorf("expected email error first, got: %v", err)
	}
}
