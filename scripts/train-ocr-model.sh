#!/bin/bash
# Fine-tuned Tesseract/deu.traineddata für Termin-Erkennung (Foto-/Brief-Import).
#
# Baut auf der in ocr-training/ vorbereiteten Umgebung auf:
#   ocr-training/tesseract-src/      - Tesseract-Quellcode inkl. gebauter Trainings-Tools
#   ocr-training/base-model/         - offizielle deu.traineddata/osd.traineddata (Startpunkt)
#   ocr-training/langdata_lstm_repo/ - deu-Sprachressourcen (unicharset etc.)
#   ocr-training/corpus/             - Trainingstext (Termine/Daten/Uhrzeiten)
#   ocr-training/output/             - Ergebnis: deu_appointments.traineddata
#
# Der gesamte ocr-training/-Ordner ist absichtlich NICHT im Git-Repo (.gitignore) -
# Quellcode-Klon + generierte Trainingsdaten gehören nicht in den App-Code-Verlauf.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/ocr-training"
SRC="$WORK/tesseract-src"
BASE="$WORK/base-model"
LANGDATA="$WORK/langdata_lstm_repo"
CORPUS="$WORK/corpus/appointments.txt"
GEN="$WORK/generated"
OUT="$WORK/output"
MAX_ITER="${MAX_ITER:-500}"

export PATH="$SRC/api:$SRC/training:$SRC:$PATH"
export TESSDATA_PREFIX="$BASE"

command -v text2image >/dev/null || { echo "text2image nicht gefunden - erst 'make training' in $SRC ausführen"; exit 1; }
command -v lstmtraining >/dev/null || { echo "lstmtraining nicht gefunden - erst 'make training' in $SRC ausführen"; exit 1; }
command -v combine_tessdata >/dev/null || { echo "combine_tessdata nicht gefunden"; exit 1; }

mkdir -p "$GEN" "$OUT"
rm -f "$GEN"/*.tif "$GEN"/*.box "$GEN"/*.lstmf "$GEN"/*.txt

# tesseract sucht den Spezial-Configfile "lstm.train" unter $TESSDATA_PREFIX/configs/ -
# das offizielle deu.traineddata/osd.traineddata-Verzeichnis (BASE) hat das nicht,
# nur der Tesseract-Quellcode selbst liefert ihn mit. Ohne ihn läuft Tesseract als
# normales OCR statt im Trainings-Modus und erzeugt nie eine .lstmf-Datei.
mkdir -p "$BASE/configs"
cp -n "$SRC/tessdata/configs/lstm.train" "$BASE/configs/lstm.train"

echo "== Schritt 2: Synthetische Trainingsbilder erzeugen (text2image) =="
FONTS=("DejaVu Sans" "Liberation Sans" "Noto Sans")
SIZES=(10 12 14)
i=0
for font in "${FONTS[@]}"; do
  for size in "${SIZES[@]}"; do
    base="$GEN/appt_${i}"
    text2image \
      --text="$CORPUS" \
      --outputbase="$base" \
      --font="$font" \
      --fonts_dir=/usr/share/fonts \
      --ptsize="$size" \
      --resolution=300 \
      --max_pages=0 \
      --degrade_image=true \
      --exposure=0
    i=$((i+1))
  done
done
echo "Erzeugte Bild/Box-Paare: $((i))"

echo "== Fehlender Schritt der Anleitung ergänzt: .lstmf-Dateien aus Bild+Box generieren =="
: > "$WORK/deu.training_files.txt"
for tif in "$GEN"/*.tif; do
  base="${tif%.tif}"
  tesseract "$tif" "$base" -l deu --psm 6 lstm.train
  echo "${base}.lstmf" >> "$WORK/deu.training_files.txt"
done
echo "Trainingsliste: $WORK/deu.training_files.txt ($(wc -l < "$WORK/deu.training_files.txt") Dateien)"

echo "== Schritt 3: LSTM-Checkpoint aus offiziellem deu.traineddata extrahieren =="
combine_tessdata -e "$BASE/deu.traineddata" "$OUT/deu.lstm"

echo "== Schritt 3b: Fine-Tuning (Transfer Learning, max. $MAX_ITER Iterationen) =="
lstmtraining \
  --continue_from "$OUT/deu.lstm" \
  --old_traineddata "$BASE/deu.traineddata" \
  --traineddata "$BASE/deu.traineddata" \
  --train_listfile "$WORK/deu.training_files.txt" \
  --model_output "$OUT/deu_ft" \
  --max_iterations "$MAX_ITER"

echo "== Schritt 4: Finales traineddata extrahieren =="
lstmtraining \
  --stop_training \
  --continue_from "$OUT/deu_ft_checkpoint" \
  --old_traineddata "$BASE/deu.traineddata" \
  --traineddata "$BASE/deu.traineddata" \
  --model_output "$OUT/deu_appointments.traineddata"

echo
echo "Fertig: $OUT/deu_appointments.traineddata"
echo "Vergleichstest: tesseract <bild> stdout -l deu_appointments --psm 6 --tessdata-dir $OUT"
echo "(Dazu zusätzlich osd.traineddata aus $BASE nach $OUT kopieren)"
