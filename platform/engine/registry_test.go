package engine

import (
	"testing"
)

type mockAdapter struct {
	name string
	tier Tier
}

func (m *mockAdapter) Name() string    { return m.name }
func (m *mockAdapter) Tier() Tier      { return m.tier }
func (m *mockAdapter) Execute(prompt string) (*RawResponse, error) {
	return &RawResponse{EngineName: m.name, Content: "mock"}, nil
}

func TestRegistry_RegisterAndGet(t *testing.T) {
	r := NewRegistry()
	a := &mockAdapter{name: "test", tier: TierDirect}
	r.Register(a)

	got := r.Get("test")
	if got == nil {
		t.Fatal("Get('test') nil döndü")
	}
	if got.Name() != "test" {
		t.Errorf("beklenen 'test', gerçek %s", got.Name())
	}
}

func TestRegistry_GetUnknown(t *testing.T) {
	r := NewRegistry()
	got := r.Get("unknown")
	if got != nil {
		t.Error("bilinmeyen adapter nil dönmeli")
	}
}

func TestRegistry_List(t *testing.T) {
	r := NewRegistry()
	r.Register(&mockAdapter{name: "a"})
	r.Register(&mockAdapter{name: "b"})
	r.Register(&mockAdapter{name: "c"})

	names := r.List()
	if len(names) != 3 {
		t.Errorf("beklenen 3, gerçek %d", len(names))
	}
}

func TestRegistry_Count(t *testing.T) {
	r := NewRegistry()
	r.Register(&mockAdapter{name: "x"})
	r.Register(&mockAdapter{name: "y"})
	if r.Count() != 2 {
		t.Errorf("beklenen 2, gerçek %d", r.Count())
	}
}

func TestRegistry_EmptyCount(t *testing.T) {
	r := NewRegistry()
	if r.Count() != 0 {
		t.Errorf("beklenen 0, gerçek %d", r.Count())
	}
}
