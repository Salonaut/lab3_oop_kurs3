"""Deterministic probability-based Battleship opponent (no random module)."""
from collections import deque

SIZE = 10
FLEET = (5, 4, 3, 3, 2)
VALID = {'.', 'M', 'H', 'S'}


def choose_shot(board: list[str], remaining: list[int]) -> tuple[int, int]:
    """Heat map from viable ship placements; focus known unsunk hits first."""
    if len(board) != SIZE or any(len(row) != SIZE or set(row) - VALID for row in board):
        raise ValueError('board must be a 10x10 grid of ./M/H/S')
    if not remaining or any(type(n) is not int or not 1 <= n <= SIZE for n in remaining):
        raise ValueError('remaining must be non-empty ship lengths between 1 and 10')
    available = {(r, c) for r in range(SIZE) for c in range(SIZE) if board[r][c] == '.'}
    if not available:
        raise ValueError('no available shots')
    hits = {(r, c) for r in range(SIZE) for c in range(SIZE) if board[r][c] == 'H'}
    # A sunk ship and each adjacent cell are impossible placements for another ship.
    forbidden = {(r, c) for r in range(SIZE) for c in range(SIZE) if board[r][c] in 'MS'}
    for r in range(SIZE):
        for c in range(SIZE):
            if board[r][c] == 'S':
                for dr in (-1, 0, 1):
                    for dc in (-1, 0, 1):
                        if 0 <= r+dr < SIZE and 0 <= c+dc < SIZE:
                            forbidden.add((r+dr, c+dc))
    placements = []
    for length in remaining:
        for r in range(SIZE):
            for c in range(SIZE):
                for dr, dc in ((0, 1), (1, 0)):
                    cells = [(r+dr*i, c+dc*i) for i in range(length)]
                    if all(0 <= rr < SIZE and 0 <= cc < SIZE and (rr, cc) not in forbidden for rr, cc in cells):
                        placements.append(cells)

    # Target mode: use the most informative placements through unsunk hits.
    max_hits = max((sum(cell in hits for cell in p) for p in placements), default=0)
    target = bool(hits and max_hits)
    scores = {cell: 0 for cell in available}
    for cells in placements:
        n_hits = sum(cell in hits for cell in cells)
        if target and n_hits != max_hits:
            continue
        if not target and n_hits:
            continue
        for cell in cells:
            if cell in scores:
                scores[cell] += 1
    # If no placements fit (inconsistent board), fall back to legal shots.
    # Prefer cardinal neighbors of hits before broad search.
    if not any(scores.values()) and hits:
        for r, c in hits:
            for dr, dc in ((-1, 0), (0, -1), (0, 1), (1, 0)):
                cell = (r+dr, c+dc)
                if cell in scores:
                    scores[cell] += 1
    # Deterministic tie breaking: center first, then row/column.
    return min(available, key=lambda p: (-scores[p], abs(p[0]-4.5)+abs(p[1]-4.5), p[0], p[1]))
