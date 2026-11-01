"""모든 테스트 공용 설정."""
from __future__ import annotations

from types import SimpleNamespace

import pytest

from src import messaging


@pytest.fixture(autouse=True)
def volumes(tmp_path, monkeypatch):
    """file-store(읽기 전용 입력) · results(결과) 볼륨을 테스트마다 새 폴더로 돌린다.

    어느 테스트도 실제 볼륨 경로를 읽거나 쓰지 않게 모든 테스트에 건다.
    """
    store = tmp_path / "file-store"
    results = tmp_path / "results"
    store.mkdir()
    results.mkdir()
    monkeypatch.setattr(messaging, "FILE_STORE_ROOT", store)
    monkeypatch.setattr(messaging, "RESULTS_ROOT", results)
    return SimpleNamespace(store=store, results=results)
