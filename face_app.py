#!/usr/bin/env python3
import sys
import os
import cv2
import pickle
import numpy as np
import face_recognition

# ============ CONFIG ============
DATASET_DIR = "../dataset"
CACHE_FILE = "../encodings.pkl"
RATIO = 0.3           # resize factor for training
TOLERANCE = 0.45      # smaller = stricter match
FORCE_UPDATE = False   # force rebuild cache
# ================================


# === Utility: Encode faces from dataset ===
def encode_faces_from_folder(dataset_dir=DATASET_DIR, ratio=RATIO):
    encodings, names = [], []
    valid_exts = (".jpg", ".jpeg", ".png", ".bmp")

    for person in os.listdir(dataset_dir):
        person_dir = os.path.join(dataset_dir, person)
        if not os.path.isdir(person_dir):
            continue

        for img_file in os.listdir(person_dir):
            if not img_file.lower().endswith(valid_exts):
                continue
            img_path = os.path.join(person_dir, img_file)
            img = face_recognition.load_image_file(img_path)
            small_img = cv2.resize(img, (0, 0), fx=ratio, fy=ratio)
            encs = face_recognition.face_encodings(small_img)
            if encs:
                encodings.append(encs[0])
                names.append(person)
    print(f"[RESULT] Encoded {len(names)} face images.")
    return encodings, names


# === Utility: Load from cache or rebuild ===
def load_or_encode_faces(force=False):
    if os.path.exists(CACHE_FILE) and not force:
        print("[INFO] Loading encodings from cache...")
        with open(CACHE_FILE, "rb") as f:
            return pickle.load(f)
    print("[INFO] Rebuilding face encodings from dataset...")
    enc, names = encode_faces_from_folder()
    with open(CACHE_FILE, "wb") as f:
        pickle.dump((enc, names), f)
    print("[INFO] Cache updated.")
    return enc, names


# === Enrollment: Register user faces ===
def enroll_user(username):
    user_dir = os.path.join(DATASET_DIR, username)
    os.makedirs(user_dir, exist_ok=True)
    valid_exts = (".jpg", ".jpeg", ".png")
    count = sum(1 for f in os.listdir(user_dir) if f.lower().endswith(valid_exts))
    print(f"[INFO] Starting enrollment for user: {username}")

    if count > 0:
        print(f"[INFO] {count} images saved. Updating encodings...")
        load_or_encode_faces(force=True)
        print(f"[SUCCESS] Enrollment completed for {username}.")
    else:
        print("[WARN] No images captured.")


# === Recognition: Authenticate via webcam ===
def recognize_live():
    print("[INFO] Loading encodings for recognition...")
    known_encodings, known_names = load_or_encode_faces(force=False)

    if not known_encodings:
        print("[ERROR] No known encodings. Please enroll first.")
        sys.exit(1)

    video = cv2.VideoCapture(0)
    if not video.isOpened():
        print("[ERROR] Cannot open webcam.")
        sys.exit(1)

    print("[INFO] Starting live recognition... Press 'q' to quit.")
    while True:
        ret, frame = video.read()
        if not ret:
            break

        small_frame = cv2.resize(frame, (0, 0), fx=0.25, fy=0.25)
        rgb_small_frame = cv2.cvtColor(small_frame, cv2.COLOR_BGR2RGB)

        face_locations = face_recognition.face_locations(rgb_small_frame, model="hog")
        face_encodings = face_recognition.face_encodings(rgb_small_frame, face_locations)

        for (top, right, bottom, left), face_encoding in zip(face_locations, face_encodings):
            matches = face_recognition.compare_faces(known_encodings, face_encoding, tolerance=TOLERANCE)
            name = "Unknown"

            face_distances = face_recognition.face_distance(known_encodings, face_encoding)
            best_idx = np.argmin(face_distances)

            if matches[best_idx]:
                name = known_names[best_idx]

            # Scale coordinates
            top *= 4
            right *= 4
            bottom *= 4
            left *= 4

            cv2.rectangle(frame, (left, top), (right, bottom), (0, 255, 0), 2)
            cv2.putText(frame, name, (left, bottom + 25), cv2.FONT_HERSHEY_DUPLEX, 1.0, (255, 255, 255), 2)

        cv2.imshow("Face Recognition", frame)

        if cv2.waitKey(1) & 0xFF == ord('q'):
            print("[INFO] Exiting recognition.")
            break

    video.release()
    cv2.destroyAllWindows()


# === Entry point ===
if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python3 face_app.py enroll <username>")
        print("  python3 face_app.py login")
        sys.exit(1)

    mode = sys.argv[1].lower()

    if mode == "enroll":
        if len(sys.argv) < 3:
            print("[ERROR] Username required for enrollment.")
            sys.exit(1)
        enroll_user(sys.argv[2])

    elif mode == "login":
        recognize_live()

    else:
        print(f"[ERROR] Unknown mode: {mode}. Use 'enroll' or 'login'.")
