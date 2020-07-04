if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

if [ ! -r "$FILE" ]; then
  echo -n "File not readable." 1>&2
  exit 1
fi

SELECTOR="head -1"
LINECOUNT=$(wc -l "$FILE" | awk '{print $1}')

# :absent by linenum
if [ "$LINENUM" ]; then
  if [ "$LINENUM" -gt "$LINECOUNT" ]; then
    echo -n "No line number $LINENUM in file." 1>&2
    exit 2
  elif [ "$LINENUM" -lt "-$LINECOUNT" ]; then
    echo -n "No line number $LINENUM in file." 1>&2
    exit 2
  elif [ "$LINENUM" -lt 0 ]; then
    LINENUM=$((LINECOUNT + LINENUM + 1))
  fi

  sed -i "${LINENUM}d${LINE}" "$FILE"
  exit -1
fi

# :absent by regexp or string-match
if [ "$REGEX" ] || [ "$STRING_MATCH" ] || [ "$LINE_MATCH" ]; then
  if [ "$REGEX" ]; then
    LINENUM=$(sed -n "${REGEX}=" "$FILE" | $SELECTOR)
  else
    if [ "$STRING_MATCH" ]; then
      LINENUM=$(grep -n -F "${STRING_MATCH}" "$FILE" | cut -d: -f1 | $SELECTOR)
    else
      LINENUM=$(grep -n -x -F "${LINE_MATCH}" "$FILE" | cut -d: -f1 | $SELECTOR)
    fi
  fi

  if [ "$LINENUM" ]; then
    sed -i "${LINENUM}d${LINE}" "$FILE"
    exit -1
  else
    exit 0
  fi
fi
