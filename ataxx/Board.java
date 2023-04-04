
/* Skeleton code copyright (C) 2008, 2022 Paul N. Hilfinger and the
 * Regents of the University of California.  Do not distribute this or any
 * derivative work without permission. */

package ataxx;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Formatter;

import java.util.function.Consumer;

import static ataxx.PieceColor.*;
import static ataxx.GameException.error;

/** An Ataxx board.   The squares are labeled by column (a char value between
 *  'a' - 2 and 'g' + 2) and row (a char value between '1' - 2 and '7'
 *  + 2) or by linearized index, an integer described below.  Values of
 *  the column outside 'a' and 'g' and of the row outside '1' to '7' denote
 *  two layers of border squares, which are always blocked.
 *  This artificial border (which is never actually printed) is a common
 *  trick that allows one to avoid testing for edge conditions.
 *  For example, to look at all the possible moves from a square, sq,
 *  on the normal board (i.e., not in the border region), one can simply
 *  look at all squares within two rows and columns of sq without worrying
 *  about going off the board. Since squares in the border region are
 *  blocked, the normal logic that prevents moving to a blocked square
 *  will apply.
 *
 *  For some purposes, it is useful to refer to squares using a single
 *  integer, which we call its "linearized index".  This is simply the
 *  number of the square in row-major order (counting from 0).
 *
 *  Moves on this board are denoted by Moves.
 *  @author Harshaan Sall
 */
class Board {


    /** Number of squares on a side of the board. */
    static final int SIDE = Move.SIDE;

    /** Length of a side + an artificial 2-deep border region.
     * This is unrelated to a move that is an "extend". */
    static final int EXTENDED_SIDE = Move.EXTENDED_SIDE;

    /** Number of consecutive non-extending moves before game ends. */
    static final int JUMP_LIMIT = 25;

    /** A new, cleared board in the initial configuration. */
    Board() {
        _board = new PieceColor[EXTENDED_SIDE * EXTENDED_SIDE];
        _allMoves = new ArrayList<Move>();
        _numPieces[RED.ordinal()] = 2;
        _numPieces[BLUE.ordinal()] = 2;
        _whoseMove = RED;
        _numJumps = 0;
        _jumpCount = new Stack<>();
        _totalMoves = 0;
        _undoSquares = new Stack<Integer>();
        _undoPieces = new Stack<PieceColor>();
        setNotifier(NOP);
        clear();
        _totalOpen = (11 * 11) - _blocked;
    }

    /** A board whose initial contents are copied from BOARD0, but whose
     *  undo history is clear, and whose notifier does nothing. */
    Board(Board board0) {
        _board = board0._board.clone();
        _totalOpen = board0.totalOpen();
        _whoseMove = board0.whoseMove();
        _winner = board0.getWinner();
        _allMoves = new ArrayList<Move>();
        _blocked = board0._blocked;
        _numPieces = board0._numPieces.clone();
        _numJumps = 0;
        _jumpCount = new Stack<>();
        _totalMoves = 0;
        _undoSquares = new Stack<Integer>();
        _undoPieces = new Stack<PieceColor>();
        setNotifier(NOP);
    }

    /** Return the linearized index of square COL ROW. */
    static int index(char col, char row) {
        return (row - '1' + 2) * EXTENDED_SIDE + (col - 'a' + 2);
    }

    /** Return the linearized index of the square that is DC columns and DR
     *  rows away from the square with index SQ. */
    static int neighbor(int sq, int dc, int dr) {
        return sq + dc + dr * EXTENDED_SIDE;
    }

    /** Clear me to my starting state, with pieces in their initial
     *  positions and no blocks. */
    void clear() {
        _numPieces[RED.ordinal()] = 2;
        _numPieces[BLUE.ordinal()] = 2;
        _blocked = 0;
        _totalOpen = 7 * 7;
        for (char bC : BC) {
            for (char bR: BCS) {
                this.set(bC, bR, BLOCKED);
                _blocked++;
            }
        }
        for (char x : BRS) {
            for (char y: BR) {
                if (!(_board[index(y, x)] == BLOCKED)) {
                    this.set(y, x, BLOCKED);
                    _blocked++;
                }
            }
        }
        for (char c : CI) {
            for (char r : RI) {
                this.set(c, r, EMPTY);
            }
        }
        this.set('a', '7', RED);
        this.set('g', '1', RED);
        this.set('a', '1', BLUE);
        this.set('g', '7', BLUE);
        _totalMoves = 0;
        _whoseMove = RED;
        _winner = null;
        announce();
    }

    /** Return the winner, if there is one yet, and otherwise null.  Returns
     *  EMPTY in the case of a draw, which can happen as a result of there
     *  having been MAX_JUMPS consecutive jumps without intervening extends,
     *  or if neither player can move and both have the same number of pieces.*/
    PieceColor getWinner() {
        return _winner;
    }

    /** Return number of red pieces on the board. */
    int redPieces() {
        return numPieces(RED);
    }

    /** Return number of blue pieces on the board. */
    int bluePieces() {
        return numPieces(BLUE);
    }

    /** Return number of COLOR pieces on the board. */
    int numPieces(PieceColor color) {
        return _numPieces[color.ordinal()];
    }

    /** Increment numPieces(COLOR) by K. */
    private void incrPieces(PieceColor color, int k) {
        _numPieces[color.ordinal()] += k;
    }

    /** The current contents of square CR, where 'a'-2 <= C <= 'g'+2, and
     *  '1'-2 <= R <= '7'+2.  Squares outside the range a1-g7 are all
     *  BLOCKED.  Returns the same value as get(index(C, R)). */
    PieceColor get(char c, char r) {
        return _board[index(c, r)];
    }

    /** Return the current contents of square with linearized index SQ. */
    PieceColor get(int sq) {
        return _board[sq];
    }

    /** Set get(C, R) to V, where 'a' <= C <= 'g', and
     *  '1' <= R <= '7'. This operation is undoable. */
    private void set(char c, char r, PieceColor v) {
        set(index(c, r), v);
    }

    /** Set square with linearized index SQ to V.  This operation is
     *  undoable. */
    private void set(int sq, PieceColor v) {
        addUndo(sq);
        _board[sq] = v;
    }

    /** Set square at C R to V (not undoable). This is used for changing
     * contents of the board without updating the undo stacks. */
    private void unrecordedSet(char c, char r, PieceColor v) {
        _board[index(c, r)] = v;
    }

    /** Set square at linearized index SQ to V (not undoable). This is used
     * for changing contents of the board without updating the undo stacks. */
    private void unrecordedSet(int sq, PieceColor v) {
        _board[sq] = v;
    }

    /** Return true iff MOVE is legal on the current board. */
    boolean legalMove(Move move) {
        if (move == null) {
            return false;
        }
        if (move.isPass()) {
            return !(canMove(whoseMove()));
        }

        char col0 = move.col0();
        char col1 = move.col1();
        char row0 = move.row0();
        char row1 = move.row1();
        if (_board[index(col0, row0)] != whoseMove()) {
            return false;
        }
        if (_board[index(col1, row1)] != EMPTY) {
            return false;
        }
        boolean validMove = move.isExtend() || move.isJump();
        if (((col0 >= 'a') && (col0 <= 'g')
                && (row0 >= '1') && (row0 <= '7')
                &&  (col1 >= 'a') && (col1 <= 'g')
                && (row1 >= '1') && (row1 <= '7'))
                && (_board[move.fromIndex()].equals(whoseMove()))) {
            return validMove;
        }
        return true;
    }

    /** Return true iff C0 R0 - C1 R1 is legal on the current board. */
    boolean legalMove(char c0, char r0, char c1, char r1) {
        return legalMove(Move.move(c0, r0, c1, r1));
    }

    /** Return true iff player WHO can move, ignoring whether it is
     *  that player's move and whether the game is over. */
    boolean canMove(PieceColor who) {
        Boolean mv;
        for (char c : CI) {
            for (char r : RI) {
                int ind = index(c, r);
                if (_board[ind] == who && (_board[neighbor(ind, +1, 0)] == EMPTY
                        || _board[neighbor(ind, +1, -1)] == EMPTY
                        || _board[neighbor(ind, 0, -1)] == EMPTY
                        || _board[neighbor(ind, -1, -1)] == EMPTY
                        || _board[neighbor(ind, -1, 0)] == EMPTY
                        || _board[neighbor(ind, -1, +1)] == EMPTY
                        || _board[neighbor(ind, 0, +1)] == EMPTY
                        || _board[neighbor(ind, +1, +1)] == EMPTY
                        || _board[neighbor(ind, +2, 0)] == EMPTY
                        || _board[neighbor(ind, +2, -2)] == EMPTY
                        || _board[neighbor(ind, 0, -2)] == EMPTY
                        || _board[neighbor(ind, -2, -2)] == EMPTY
                        || _board[neighbor(ind, -2, 0)] == EMPTY
                        || _board[neighbor(ind, -2, +2)] == EMPTY
                        || _board[neighbor(ind, 0, +2)] == EMPTY
                        || _board[neighbor(ind, +2, +2)] == EMPTY
                    )) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Return the color of the player who has the next move.  The
     *  value is arbitrary if the game is over. */
    PieceColor whoseMove() {
        return _whoseMove;
    }

    /** Return total number of moves and passes since the last
     *  clear or the creation of the board. */
    int numMoves() {
        return _totalMoves;
    }

    /** Return number of non-pass moves made in the current game since the
     *  last extend move added a piece to the board (or since the
     *  start of the game). Used to detect end-of-game. */
    int numJumps() {
        return _numJumps;
    }

    /** Assuming MOVE has the format "-" or "C0R0-C1R1", make the denoted
     *  move ("-" means "pass"). */
    void makeMove(String move) {
        if (move.equals("-")) {
            makeMove(Move.pass());
        } else {
            makeMove(Move.move(move.charAt(0), move.charAt(1), move.charAt(3),
                    move.charAt(4)));
        }
    }

    /** Perform the move C0R0-C1R1, or pass if C0 is '-'.  For moves
     *  other than pass, assumes that legalMove(C0, R0, C1, R1). */
    void makeMove(char c0, char r0, char c1, char r1) {
        if (c0 == '-') {
            makeMove(Move.pass());
        } else {
            makeMove(Move.move(c0, r0, c1, r1));
        }
    }

    /** Make the MOVE on this Board, assuming it is legal. */
    void makeMove(Move move) {
        if (!legalMove(move)) {
            throw error("Illegal move: %s", move);
        }
        if (move.isPass()) {
            pass();
            return;
        }
        _allMoves.add(move); startUndo();
        PieceColor opponent = _whoseMove.opposite();
        if (move.isExtend()) {
            int counter = 0;
            int ind = move.toIndex(); set(ind, whoseMove());
            for (int c = -1; c <= 1; c++) {
                for (int r = -1; r <= 1; r++) {
                    if (_board[neighbor(ind, c, r)] == opponent) {
                        set(neighbor(ind, c, r), whoseMove()); counter++;
                    }
                }
            }
            _totalMoves++; _numJumps = 0;
            _jumpCount.push(_numJumps);
            incrPieces(whoseMove(), counter + 1);
            incrPieces(whoseMove().opposite(), -counter);
        }
        if (move.isJump()) {
            int counter2 = 0;
            int ind2 = move.toIndex(); int from = move.fromIndex();
            set(from, EMPTY); set(ind2, whoseMove());
            for (int c = -1; c <= 1; c++) {
                for (int r = -1; r <= 1; r++) {
                    if (_board[neighbor(ind2, c, r)] == opponent) {
                        set(neighbor(ind2, c, r), whoseMove()); counter2++;
                    }
                }
            }
            incrPieces(whoseMove(), counter2);
            incrPieces(whoseMove().opposite(), -counter2);
            _totalMoves++; _jumpCount.push(_numJumps); _numJumps++;
        }
        _whoseMove = opponent;
        if (!canMove(RED) && !canMove(BLUE) || _numJumps == (5 * 5)) {
            if (bluePieces() > redPieces()) {
                _winner = BLUE;
            } else if (bluePieces() < redPieces()) {
                _winner = RED;
            } else {
                _winner = EMPTY;
            }
        }
        if (numPieces(whoseMove()) == 0) {
            _winner = whoseMove().opposite();
        }
        announce();
    }

    /** Update to indicate that the current player passes, assuming it
     *  is legal to do so. Passing is undoable. */
    void pass() {
        assert !canMove(_whoseMove);
        _totalMoves++;
        startUndo();
        _whoseMove = _whoseMove.opposite();
        announce();
    }

    /** Undo the last move. */
    void undo() {
        while (!(_undoSquares.peek() == null)) {
            PieceColor currMove = get(_undoSquares.peek());
            int ind = _undoSquares.pop();
            PieceColor prevMove = _undoPieces.pop();
            incrPieces(prevMove, 1);
            incrPieces(currMove, -1);
            unrecordedSet(ind, prevMove);
        }
        _undoSquares.pop();
        _totalMoves--;
        _numJumps = _jumpCount.pop();
        _whoseMove = _whoseMove.opposite();
        _allMoves.remove(_allMoves.size() - 1);
        _winner = null;
        announce();
    }

    /** Indicate beginning of a move in the undo stack. See the
     * _undoSquares and _undoPieces instance variable comments for
     * details on how the beginning of moves are marked. */
    private void startUndo() {
        _undoSquares.push(null);
    }

    /** Add an undo action for changing SQ on current board. */
    private void addUndo(int sq) {
        _undoSquares.push(sq);
        _undoPieces.push(get(sq));
    }

    /** Return true iff it is legal to place a block at C R. */
    boolean legalBlock(char c, char r) {
        if (!_allMoves.isEmpty()) {
            return false;
        } else {
            int ind = index(c, r);
            return _board[ind].equals(EMPTY);
        }
    }

    /** Return true iff it is legal to place a block at CR. */
    boolean legalBlock(String cr) {
        return legalBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Set a block on the square C R and its reflections across the middle
     *  row and/or column, if that square is unoccupied and not
     *  in one of the corners. Has no effect if any of the squares is
     *  already occupied by a block.  It is an error to place a block on a
     *  piece. */
    void setBlock(char c, char r) {
        if (!legalBlock(c, r)) {
            throw error("illegal block placement");
        }
        if (!(_board[index(c, r)] == BLOCKED)) {
            unrecordedSet(c, r, BLOCKED);
            _blocked++;
            _totalOpen--;
            char symCol = (char) (('a' + 'g') - c);
            char symRow = (char) (('1' + '7') - r);
            if (!(_board[index(c, symRow)] == BLOCKED)) {
                unrecordedSet(c, symRow, BLOCKED);
                _totalOpen--;
                _blocked++;
            }
            if (!(_board[index(symCol, r)] == BLOCKED)) {
                unrecordedSet(symCol, symRow, BLOCKED);
                _totalOpen--;
                _blocked++;
            }
            if (!(_board[index(symCol, r)] == BLOCKED)) {
                unrecordedSet(symCol, r, BLOCKED);
                _totalOpen--;
                _blocked++;
            }
        }
        if (!canMove(RED) && !canMove(BLUE)) {
            _winner = EMPTY;
        }
        announce();
    }

    /** Place a block at CR. */
    void setBlock(String cr) {
        setBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Return total number of unblocked squares. */
    int totalOpen() {
        return _totalOpen;
    }

    /** Return a list of all moves made since the last clear (or start of
     *  game). */
    List<Move> allMoves() {
        return new ArrayList<Move>();
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Board)) {
            return false;
        }
        Board other = (Board) obj;
        return Arrays.equals(_board, other._board);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_board);
    }

    /** Return a text depiction of the board.  If LEGEND, supply row and
     *  column numbers around the edges. */
    String toString(boolean legend) {
        Formatter out = new Formatter();
        for (char r = '7'; r >= '1'; r -= 1) {
            if (legend) {
                out.format("%c", r);
            }
            out.format(" ");
            for (char c = 'a'; c <= 'g'; c += 1) {
                switch (get(c, r)) {
                case RED:
                    out.format(" r");
                    break;
                case BLUE:
                    out.format(" b");
                    break;
                case BLOCKED:
                    out.format(" X");
                    break;
                case EMPTY:
                    out.format(" -");
                    break;
                default:
                    break;
                }
            }
            out.format("%n");
        }
        if (legend) {
            out.format("   a b c d e f g");
        }
        return out.toString();
    }

    /** Set my notifier to NOTIFY. */
    public void setNotifier(Consumer<Board> notify) {
        _notifier = notify;
        announce();
    }

    /** Take any action that has been set for a change in my state. */
    private void announce() {
        _notifier.accept(this);
    }

    /** A notifier that does nothing. */
    private static final Consumer<Board> NOP = (s) -> { };

    /** Use _notifier.accept(this) to announce changes to this board. */
    private Consumer<Board> _notifier;

    /** For reasons of efficiency in copying the board,
     *  we use a 1D array to represent it, using the usual access
     *  algorithm: row r, column c => index(r, c).
     *
     *  Next, instead of using a 7x7 board, we use an 11x11 board in
     *  which the outer two rows and columns are blocks, and
     *  row 2, column 2 actually represents row 0, column 0
     *  of the real board.  As a result of this trick, there is no
     *  need to special-case being near the edge: we don't move
     *  off the edge because it looks blocked.
     *
     *  Using characters as indices, it follows that if 'a' <= c <= 'g'
     *  and '1' <= r <= '7', then row r, column c of the board corresponds
     *  to _board[(c -'a' + 2) + 11 (r - '1' + 2) ]. */
    private final PieceColor[] _board;

    /** Player that is next to move. */
    private PieceColor _whoseMove;

    /** Number of consecutive non-extending moves since the
     *  last clear or the beginning of the game. */
    private int _numJumps;

    /** Total number of unblocked squares. */
    private int _totalOpen;

    /** Number of blue and red pieces, indexed by the ordinal positions of
     *  enumerals BLUE and RED. */
    private int[] _numPieces = new int[BLUE.ordinal() + 1];

    /** Set to winner when game ends (EMPTY if tie).  Otherwise is null. */
    private PieceColor _winner;

    /** List of all (non-undone) moves since the last clear or beginning of
     *  the game. */
    private ArrayList<Move> _allMoves;

    /* The undo stack. We keep a stack of squares that have changed and
     * their previous contents.  Any given move may involve several such
     * changes, so we mark the start of the changes for each move (including
     * passes) with a null. */

    /** Stack of linearized indices of squares that have been modified and
     *  not undone. Nulls mark the beginnings of full moves. */
    private Stack<Integer> _undoSquares;
    /** Stack of pieces formally at corresponding squares in _UNDOSQUARES. */
    private Stack<PieceColor> _undoPieces;

    /** Stack jump. */
    private Stack<Integer> _jumpCount;

    /** numMoves. */
    private int _totalMoves;

    /** Blocked Squares. */
    private int _blocked;

    /** colIndex. */
    private static final char[]
            CI = {'a', 'b', 'c', 'd', 'e', 'f', 'g'};

    /** rowIndex. */
    static final char[]
            RI = {'1', '2', '3', '4', '5', '6', '7'};

    /** BlockedcolIndex. */
    static final char[]
            BC = {'a' - 2, 'a' - 1, 'g' + 1, 'g' + 2};

    /** BlockedrowIndex. */
    static final char[]
            BR
            = {95, 96, 'a', 'b', 'c', 'd', 'e', 'f', 'g', 104, 105};

    /** BlockedrowIndex. */
    static final char[]
            BRS = {'1' - 2, '1' - 1, '7' + 1, '7' + 2};

    /** BlockedcolIndex. */
    static final char[] BCS =
    {47, 48, '1', '2', '3', '4', '5', '6', '7', 56, 57};
}
