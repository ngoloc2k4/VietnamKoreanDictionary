import sqlite3
import sys

def verify_schema():
    db_path = "app/src/main/assets/databases/korvi_dictionary.db"
    conn = sqlite3.connect(db_path)
    try:
        cur = conn.cursor()
        
        # Check tables
        cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
        tables = [r[0] for r in cur.fetchall()]
        print("Tables in DB:", tables)
        assert "words" in tables, "Table 'words' missing!"
        assert "search_history" in tables, "Table 'search_history' missing!"
        assert "translation_history" in tables, "Table 'translation_history' missing!"
        assert "room_master_table" in tables, "Table 'room_master_table' missing!"
        
        # Check room_master_table hash
        cur.execute("SELECT identity_hash FROM room_master_table WHERE id = 42")
        row = cur.fetchone()
        print("room_master_table hash:", row[0] if row else "None")
        assert row and row[0] == "00db5e670d5cb8ee7d3e8e691a45e4ee", "Identity hash mismatch!"
        
        # Check columns of words table
        cur.execute("PRAGMA table_info('words')")
        cols = cur.fetchall()
        print(f"Columns in words table ({len(cols)} total):")
        expected_cols = [
            ("id", "INTEGER", 1, 1), # name, type, notnull, pk
            ("korean", "TEXT", 1, 0),
            ("vietnamese", "TEXT", 1, 0),
            ("pronunciation", "TEXT", 1, 0),
            ("partOfSpeech", "TEXT", 1, 0),
            ("hanja", "TEXT", 1, 0),
            ("hanViet", "TEXT", 1, 0),
            ("definition", "TEXT", 1, 0),
            ("level", "TEXT", 1, 0),
            ("topikLevelNumber", "INTEGER", 1, 0),
            ("examFrequency", "TEXT", 1, 0),
            ("examAppearances", "INTEGER", 1, 0),
            ("rootForm", "TEXT", 1, 0),
            ("presentTense", "TEXT", 1, 0),
            ("pastTense", "TEXT", 1, 0),
            ("futureTense", "TEXT", 1, 0),
            ("formalHigh", "TEXT", 1, 0),
            ("politeLife", "TEXT", 1, 0),
            ("informal", "TEXT", 1, 0),
            ("honorificSubject", "TEXT", 1, 0),
            ("grammarNotes", "TEXT", 1, 0),
            ("examplesText", "TEXT", 1, 0),
            ("synonyms", "TEXT", 1, 0),
            ("antonyms", "TEXT", 1, 0),
            ("isFavorite", "INTEGER", 1, 0),
            ("isMastered", "INTEGER", 1, 0),
            ("reviewCount", "INTEGER", 1, 0),
            ("lastReviewedAt", "INTEGER", 1, 0),
        ]
        
        assert len(cols) == len(expected_cols), f"Expected {len(expected_cols)} columns, found {len(cols)}"
        for actual, expected in zip(cols, expected_cols):
            # actual: (cid, name, type, notnull, dflt_value, pk)
            cid, name, col_type, notnull, dflt, pk = actual
            exp_name, exp_type, exp_notnull, exp_pk = expected
            assert name == exp_name, f"Column name mismatch: {name} != {exp_name}"
            assert col_type == exp_type, f"Column type mismatch for {name}: {col_type} != {exp_type}"
            assert notnull == exp_notnull, f"Column notnull mismatch for {name}: {notnull} != {exp_notnull}"
            assert pk == exp_pk, f"Column pk mismatch for {name}: {pk} != {exp_pk}"
            
        print("ALL 28 COLUMNS MATCH ROOM SPECIFICATION EXACTLY!")
        
        # Check indices on words
        cur.execute("PRAGMA index_list('words')")
        indices = [r for r in cur.fetchall() if r[1] != 'sqlite_autoindex_words_1']
        assert len(indices) == 0, f"Expected 0 custom indices, found: {indices}"
        print("INDEX CHECK PASSED: 0 unexpected indices (matches Room TableInfo)!")
        
        # Check total words count
        cur.execute("SELECT COUNT(*) FROM words")
        total = cur.fetchone()[0]
        print(f"TOTAL WORDS IN DB: {total}")
        assert total >= 75000, f"Expected >= 75000 words, found {total}"
        print("SCHEMA VERIFICATION 100% SUCCESSFUL!")
    finally:
        conn.close()

if __name__ == "__main__":
    verify_schema()
