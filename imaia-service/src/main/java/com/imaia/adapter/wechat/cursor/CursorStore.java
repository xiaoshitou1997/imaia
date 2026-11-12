package com.imaia.adapter.wechat.cursor;

/**
 * Persistence of the last handled event cursor. The stream resumes from this cursor,
 * so losing it means reprocessing events that already succeeded.
 */
public interface CursorStore {

    /** The last handled cursor, or null when nothing has been handled yet. */
    String load();

    /** Records a cursor after its event has been handled completely. */
    void save(String cursor);

    /** Forgets the cursor; the stream is rebuilt from the live tail. */
    void clear();
}
