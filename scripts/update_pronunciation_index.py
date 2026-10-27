import sqlite3
import unicodedata
import re

DB_PATH = "app/src/main/assets/databases/korvi_dictionary.db"

def strip_accents(t):
    if not t:
        return ""
    t = t.replace("đ", "d").replace("Đ", "D")
    t = unicodedata.normalize("NFD", t)
    return "".join(c for c in t if unicodedata.category(c) != "Mn").lower().strip()

CHOSUNG_LIST = ['ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ']

def extract_chosung(text):
    if not text:
        return ""
    res = []
    for ch in text:
        code = ord(ch)
        if 0xAC00 <= code <= 0xD7A3:
            s_idx = code - 0xAC00
            cho = s_idx // (21 * 28)
            res.append(CHOSUNG_LIST[cho])
        elif ch.isalnum():
            res.append(ch)
    return "".join(res)

def update_pronunciation_indices():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    cur.execute("SELECT id, korean, vietnamese, pronunciation FROM words")
    rows = cur.fetchall()
    print(f"Updating pronunciation compound index for {len(rows)} words...")

    updates = []
    for r_id, kr, vn, pron in rows:
        # Extract base pronunciation like [gong-bu-ha-da]
        base_pron = pron.split('•')[0].strip() if '•' in pron else pron.strip()
        if not base_pron.startswith('['):
            base_pron = f"[{base_pron}]"
        
        rom_clean = base_pron.replace('[', '').replace(']', '').replace('-', '').lower().strip()
        cho = extract_chosung(kr)
        vn_unacc = strip_accents(vn)
        
        compound = f"{base_pron} {rom_clean} • {cho} • {vn_unacc}"
        updates.append((compound, r_id))
        
        if len(updates) >= 10000:
            cur.executemany("UPDATE words SET pronunciation = ? WHERE id = ?", updates)
            conn.commit()
            updates = []
            print(f"Updated up to {r_id}...")

    if updates:
        cur.executemany("UPDATE words SET pronunciation = ? WHERE id = ?", updates)
        conn.commit()

    # Verify vacuum
    cur.execute("VACUUM")
    conn.commit()
    conn.close()
    print("✓ Successfully updated all pronunciation indices!")

if __name__ == "__main__":
    update_pronunciation_indices()
