if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

if [ ! -r "$FILE" ]; then
  echo -n "File not readable." 1>&2
  exit 1
fi

LINECOUNT=$(wc -l "$FILE" | awk '{print $1}')

# :present by linenum
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

  echo 1
  LINECONTENT=$(sed -n "${LINENUM}p" "$FILE")
  if [ "$LINECONTENT" == "$LINE" ]; then
    exit 0
  else
    echo 2
    sed -i "${LINENUM}c${LINE}" "$FILE"
    exit -1
  fi
fi

# :present by regexp
if [ "$REGEX" ]; then
  LINENUMS=$(sed -n "${REGEX}=" "$FILE" | $SELECTOR)

  if [ "$LINENUMS" ]; then
    REVERSE=""
    for i in ${LINENUMS}; do REVERSE="${i} ${REVERSE}"; done
    EXIT=0
    for LINENUM in $REVERSE; do
      echo 3
      LINECONTENT=$(sed -n "${LINENUM}p" "$FILE")
      if [ "$LINECONTENT" != "$LINE" ]; then
        echo 4
        sed -i "${LINENUM}c${LINE}" "$FILE"
        EXIT=-1
      fi
    done
    exit $EXIT
  else
    if [ "$AFTER" ]; then
      echo 5
      MATCHPOINTS=$(sed -n "${AFTER}=" "$FILE" | $SELECTOR)
      REVERSE=""
      for i in ${MATCHPOINTS}; do REVERSE="${i} ${REVERSE}"; done
      EXIT=0
      for LINENUM in $REVERSE; do
        echo 6
        LINECONTENT=$(sed -n "$((LINENUM+1))p" "$FILE")
        if [ "$LINECONTENT" != "$LINE" ]; then
          echo 7
          sed -i "${LINENUM}a${LINE}" "$FILE"
          EXIT=-1
        fi
      done
      exit $EXIT
    elif [ "$BEFORE" ]; then
      echo 8
      MATCHPOINTS=$(sed -n "${BEFORE}=" "$FILE" | $SELECTOR)
      REVERSE=""
      for i in ${MATCHPOINTS}; do REVERSE="${i} ${REVERSE}"; done
      EXIT=0
      for LINENUM in $REVERSE; do
        echo 9
        LINECONTENT=$(if [ $LINENUM -gt 1 ]; then sed -n "$((LINENUM-1))p" "$FILE"; fi)
        if [ "$LINECONTENT" != "$LINE" ]; then
          echo 10
          sed -i "${LINENUM}i${LINE}" "$FILE"
          EXIT=-1
        fi
      done
      exit $EXIT
    else
      echo 11
      sed -i "\$a${LINE}" "$FILE"
      exit -1
    fi
  fi
fi

echo "script error" 1>&2
exit 1
