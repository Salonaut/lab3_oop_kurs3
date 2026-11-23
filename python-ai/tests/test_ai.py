import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parents[1]))

from fastapi.testclient import TestClient
from ai import choose_shot
from main import app

EMPTY = ['.' * 10 for _ in range(10)]


def test_deterministic_and_legal():
    assert choose_shot(EMPTY, [5, 4, 3, 3, 2]) == choose_shot(EMPTY, [5, 4, 3, 3, 2])
    r, c = choose_shot(EMPTY, [5, 4, 3, 3, 2])
    assert (r, c) in {(i, j) for i in range(10) for j in range(10)}


def test_targets_neighbor_of_hit():
    b = EMPTY.copy()
    b[4] = '....H.....'
    r, c = choose_shot(b, [3])
    assert abs(r - 4) + abs(c - 4) == 1


def test_never_chooses_shot_cell():
    b = ['M' * 10 for _ in range(10)]
    b[0] = '.MMMMMMMMM'
    assert choose_shot(b, [2]) == (0, 0)


def test_api_health_and_move():
    client = TestClient(app)
    assert client.get('/health').json() == {'status': 'ok'}
    response = client.post('/api/ai/move', json={'board': EMPTY, 'remaining': [5, 4, 3, 3, 2]})
    assert response.status_code == 200
    assert response.json()['strategy'] == 'deterministic-probability'


def test_rejects_invalid_board():
    response = TestClient(app).post('/api/ai/move', json={'board': ['x'*10]*10, 'remaining': [3]})
    assert response.status_code == 422
