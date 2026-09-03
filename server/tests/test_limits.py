"""The spend guards, exercised directly.

These bound the bill when the bearer token leaks — which it will, since it ships inside a
distributed app — so each tier gets a test that proves it actually stops.
"""

import pytest

from app.limits import InstallQuota, Limiter, RateLimitExceeded


def _limiter(**kw):
    base = dict(per_minute=5, per_ip_daily=20, global_daily=50)
    return Limiter(**{**base, **kw})


def _drain(limiter, caller, n, now=0.0):
    for i in range(n):
        limiter.check(caller, now + i * 0.001)


def test_per_minute_window_stops_a_hot_loop():
    limiter = _limiter()
    _drain(limiter, "ip-a", 5)

    with pytest.raises(RateLimitExceeded) as e:
        limiter.check("ip-a", 0.01)
    assert e.value.scope == "per_minute"


def test_the_window_slides():
    limiter = _limiter()
    _drain(limiter, "ip-a", 5)

    # Past the window, the same caller is allowed again.
    limiter.check("ip-a", 61.0)


def test_per_ip_daily_stops_a_slow_drip():
    limiter = _limiter()
    # Paced to never trip the per-minute window — the gap the daily tier exists to close.
    for i in range(20):
        limiter.check("ip-a", i * 61.0)

    with pytest.raises(RateLimitExceeded) as e:
        limiter.check("ip-a", 20 * 61.0)
    assert e.value.scope == "ip_daily"


def test_global_budget_stops_a_spray_across_many_addresses():
    limiter = _limiter(per_ip_daily=1000, max_tracked_callers=10_000)
    for i in range(50):
        limiter.check(f"ip-{i}", i * 61.0)

    # A distributed attacker defeats every per-IP tier; this is the one that bounds the bill.
    with pytest.raises(RateLimitExceeded) as e:
        limiter.check("ip-new", 51 * 61.0)
    assert e.value.scope == "global_daily"
    assert limiter.used_today == 50


def test_a_rejected_call_is_not_billed_against_the_budget():
    limiter = _limiter()
    _drain(limiter, "ip-a", 5)
    before = limiter.used_today

    with pytest.raises(RateLimitExceeded):
        limiter.check("ip-a", 0.01)

    # A shed request costs nothing upstream, so it must not consume budget either.
    assert limiter.used_today == before


def test_tracked_caller_map_stays_bounded():
    limiter = _limiter(per_minute=1000, per_ip_daily=1000, global_daily=10_000, max_tracked_callers=3)

    shed = 0
    for i in range(50):
        try:
            limiter.check(f"ip-{i}", 0.0)
        except RateLimitExceeded as e:
            assert e.scope == "tracked_callers"
            shed += 1

    assert len(limiter._windows) <= 3
    assert shed > 0


def test_counters_reset_on_a_new_day(monkeypatch):
    import datetime as dt

    limiter = _limiter()
    _drain(limiter, "ip-a", 5)
    assert limiter.used_today == 5

    real = dt.date
    monkeypatch.setattr(Limiter, "_today", staticmethod(lambda: real(2099, 1, 1)))
    limiter.check("ip-a", 0.02)

    assert limiter.used_today == 1


def test_install_quota_counts_down_and_stops():
    quota = InstallQuota(per_install_daily=3, new_ids_per_ip_daily=5)

    assert [quota.check("id-a", "ip-1") for _ in range(3)] == [2, 1, 0]
    with pytest.raises(RateLimitExceeded) as e:
        quota.check("id-a", "ip-1")
    assert e.value.scope == "install_daily"
    # Another install is unaffected.
    assert quota.check("id-b", "ip-1") == 2


def test_install_quota_throttles_id_rotation_from_one_address():
    quota = InstallQuota(per_install_daily=100, new_ids_per_ip_daily=2)
    quota.check("id-a", "ip-1")
    quota.check("id-b", "ip-1")

    with pytest.raises(RateLimitExceeded) as e:
        quota.check("id-c", "ip-1")
    assert e.value.scope == "new_ids_per_ip"
    # Known ids keep working, and another address has its own count.
    quota.check("id-a", "ip-1")
    quota.check("id-c", "ip-2")
