Attribute VB_Name = "SRGzip"
Option Explicit
'RFC 1951/1952 decoder; encoder uses valid stored DEFLATE blocks.
'The small Telegram index is bounded/pruned before publication, never truncated.
Private Type Huffman
    Count(0 To 15) As Long
    Symbol(0 To 319) As Long
End Type
Private source() As Byte, byteAt As Long, bitAt As Long, payloadEnd As Long

Private Function Bits(ByVal count As Long) As Long
    Dim i As Long
    For i = 0 To count - 1
        Need byteAt < payloadEnd, "Truncated gzip bitstream."
        If (source(byteAt) And CLng(2 ^ bitAt)) <> 0 Then Bits = Bits + CLng(2 ^ i)
        bitAt = bitAt + 1
        If bitAt = 8 Then bitAt = 0: byteAt = byteAt + 1
    Next i
End Function

Private Sub Build(ByRef lengths() As Long, ByVal start As Long, ByVal count As Long, ByRef h As Huffman)
    Dim i As Long, offsets(0 To 15) As Long, remaining As Long
    For i = 0 To 15: h.Count(i) = 0: Next i
    For i = 0 To count - 1
        Need lengths(start + i) >= 0 And lengths(start + i) <= 15, "Invalid Huffman length."
        h.Count(lengths(start + i)) = h.Count(lengths(start + i)) + 1
    Next i
    remaining = 1
    For i = 1 To 15
        remaining = remaining * 2 - h.Count(i): Need remaining >= 0, "Oversubscribed Huffman tree."
    Next i
    offsets(1) = 0
    For i = 1 To 14: offsets(i + 1) = offsets(i) + h.Count(i): Next i
    For i = 0 To count - 1
        If lengths(start + i) Then
            h.Symbol(offsets(lengths(start + i))) = i
            offsets(lengths(start + i)) = offsets(lengths(start + i)) + 1
        End If
    Next i
End Sub

Private Function Decode(ByRef h As Huffman) As Long
    Dim code As Long, first As Long, index As Long, width As Long, count As Long
    For width = 1 To 15
        code = code Or Bits(1): count = h.Count(width)
        If code >= first And code < first + count Then Decode = h.Symbol(index + code - first): Exit Function
        index = index + count: first = (first + count) * 2: code = code * 2
    Next width
    Need False, "Invalid Huffman symbol."
End Function

Private Function Crc(ByRef raw() As Byte) As Long
    Dim value As Long, i As Long, j As Long, low As Boolean
    value = -1
    For i = 0 To ByteCount(raw) - 1
        value = value Xor CLng(raw(i))
        For j = 1 To 8
            low = (value And 1) <> 0
            If value < 0 Then value = ((value And &H7FFFFFFF) \ 2) Or &H40000000 Else value = value \ 2
            If low Then value = value Xor &HEDB88320
        Next j
    Next i
    Crc = Not value
End Function

Private Sub StoreLong(ByRef target() As Byte, ByVal offset As Long, ByVal value As Long)
    Dim i As Long
    For i = 0 To 3
        target(offset + i) = value And 255
        If value < 0 Then value = ((value And &H7FFFFFFF) \ 256) Or &H800000 Else value = value \ 256
    Next i
End Sub

Private Function ReadLong(ByRef raw() As Byte, ByVal offset As Long) As Long
    ReadLong = CLng(raw(offset)) Or CLng(raw(offset + 1)) * 256 Or CLng(raw(offset + 2)) * 65536
    ReadLong = ReadLong Or CLng(raw(offset + 3) And 127) * 16777216
    If raw(offset + 3) >= 128 Then ReadLong = ReadLong Or &H80000000
End Function

Public Function Gzip(ByRef raw() As Byte) As Byte()
    Dim result() As Byte, count As Long, blocks As Long, p As Long, used As Long, n As Long, i As Long
    count = ByteCount(raw): blocks = (count + 65534) \ 65535
    If blocks = 0 Then blocks = 1
    ReDim result(0 To 18 + count + blocks * 5 - 1)
    result(0) = 31: result(1) = 139: result(2) = 8: result(9) = 255: p = 10
    Do
        n = count - used: If n > 65535 Then n = 65535
        If used + n = count Then result(p) = 1
        result(p + 1) = n And 255: result(p + 2) = n \ 256
        result(p + 3) = (Not n) And 255: result(p + 4) = ((Not n) And 65535) \ 256
        p = p + 5
        For i = 0 To n - 1: result(p + i) = raw(used + i): Next i
        p = p + n: used = used + n
        If used = count Then Exit Do
    Loop
    StoreLong result, p, Crc(raw): StoreLong result, p + 4, count: Gzip = result
End Function

Public Function Gunzip(ByRef raw() As Byte) As Byte()
    Dim result() As Byte, size As Long, made As Long, flags As Long, final As Long, kind As Long
    Dim literal As Huffman, distance As Huffman, codeTree As Huffman
    Dim lens(0 To 319) As Long, clens(0 To 18) As Long, order As Variant
    Dim nlit As Long, ndist As Long, ncode As Long, i As Long, j As Long, sym As Long, repeat As Long
    Dim value As Long, length As Long, offset As Long, n As Long, crcValue As Long, discarded As Long
    Dim lb As Variant, le As Variant, db As Variant, de As Variant
    Need ByteCount(raw) >= 18, "Truncated gzip."
    Need raw(0) = 31 And raw(1) = 139 And raw(2) = 8 And (raw(3) And 224) = 0, "Unsupported gzip header."
    size = ReadLong(raw, ByteCount(raw) - 4): Need size >= 0 And size <= 8388608, "Gzip exceeds 8 MiB."
    ReDim result(0 To size): source = raw: byteAt = 10: bitAt = 0: payloadEnd = ByteCount(raw) - 8
    flags = raw(3)
    If (flags And 4) <> 0 Then
        n = Bits(16): byteAt = byteAt + n: Need byteAt < payloadEnd, "Invalid gzip extra field."
    End If
    If (flags And 8) <> 0 Then
        Do: discarded = Bits(8): Loop Until discarded = 0
    End If
    If (flags And 16) <> 0 Then
        Do: discarded = Bits(8): Loop Until discarded = 0
    End If
    If (flags And 2) <> 0 Then discarded = Bits(16)
    lb = Array(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    le = Array(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    db = Array(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    de = Array(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    order = Array(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
    Do
        final = Bits(1): kind = Bits(2)
        If kind = 0 Then
            If bitAt <> 0 Then bitAt = 0: byteAt = byteAt + 1
            n = Bits(16): discarded = Bits(16)
            Need (n Xor discarded) = 65535 And made + n <= size, "Invalid stored gzip block."
            For i = 1 To n: result(made) = Bits(8): made = made + 1: Next i
        Else
            Need kind <> 3, "Invalid gzip block type."
            If kind = 1 Then
                For i = 0 To 287
                    Select Case i
                        Case 0 To 143: lens(i) = 8
                        Case 144 To 255: lens(i) = 9
                        Case 256 To 279: lens(i) = 7
                        Case Else: lens(i) = 8
                    End Select
                Next i
                Build lens, 0, 288, literal
                For i = 0 To 31: lens(i) = 5: Next i
                Build lens, 0, 32, distance
            Else
                nlit = Bits(5) + 257: ndist = Bits(5) + 1: ncode = Bits(4) + 4
                Need nlit <= 286, "Invalid literal count."
                For i = 0 To 18: clens(i) = 0: Next i
                For i = 0 To ncode - 1: clens(order(i)) = Bits(3): Next i
                Build clens, 0, 19, codeTree: i = 0
                Do While i < nlit + ndist
                    sym = Decode(codeTree)
                    If sym < 16 Then
                        lens(i) = sym: i = i + 1
                    Else
                        value = 0
                        Select Case sym
                            Case 16
                                Need i > 0, "Missing repeated Huffman length."
                                value = lens(i - 1): repeat = Bits(2) + 3
                            Case 17: repeat = Bits(3) + 3
                            Case 18: repeat = Bits(7) + 11
                            Case Else: Need False, "Invalid Huffman repeat."
                        End Select
                        Need i + repeat <= nlit + ndist, "Huffman repeat overflow."
                        For j = 1 To repeat: lens(i) = value: i = i + 1: Next j
                    End If
                Loop
                Need lens(256) > 0, "Missing gzip end marker."
                Build lens, 0, nlit, literal: Build lens, nlit, ndist, distance
            End If
            Do
                sym = Decode(literal)
                If sym = 256 Then Exit Do
                If sym < 256 Then
                    Need made < size, "Gzip output overflow."
                    result(made) = sym: made = made + 1
                Else
                    Need sym >= 257 And sym <= 285, "Invalid gzip length."
                    length = lb(sym - 257) + Bits(le(sym - 257)): sym = Decode(distance)
                    Need sym < 30, "Invalid gzip distance."
                    offset = db(sym) + Bits(de(sym))
                    Need offset <= made And made + length <= size, "Invalid gzip back-reference."
                    For j = 1 To length: result(made) = result(made - offset): made = made + 1: Next j
                End If
            Loop
        End If
    Loop Until final = 1
    Need made = size, "Gzip length mismatch."
    Need byteAt + IIf(bitAt > 0, 1, 0) = payloadEnd, "Trailing gzip payload is unsupported."
    If size Then ReDim Preserve result(0 To size - 1) Else Erase result
    crcValue = Crc(result): Need crcValue = ReadLong(raw, ByteCount(raw) - 8), "Gzip checksum mismatch."
    Gunzip = result: Erase source
End Function
