"""HTTP API for a Battleship computer player."""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from ai import choose_shot

app = FastAPI(title='Sea Battle AI', version='1.0.0')


class MoveRequest(BaseModel):
    board: list[str] = Field(min_length=10, max_length=10)
    remaining: list[int] = Field(min_length=1)


class MoveResponse(BaseModel):
    row: int
    col: int
    strategy: str = 'deterministic-probability'


@app.get('/health')
def health():
    return {'status': 'ok'}


@app.post('/api/ai/move', response_model=MoveResponse)
def move(request: MoveRequest):
    try:
        row, col = choose_shot(request.board, request.remaining)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return MoveResponse(row=row, col=col)
