Attribute VB_Name = "SRJson"
Option Explicit
Private mText As String, mPos As Long, mDepth As Long

Public Sub Need(ByVal condition As Boolean, ByVal message As String)
    If Not condition Then Err.Raise vbObjectError + 2401, "ShiftReport", message
End Sub

Public Function Matches(ByVal value As String, ByVal pattern As String) As Boolean
    Dim rx As Object: Set rx = CreateObject("VBScript.RegExp")
    rx.Pattern = pattern: Matches = rx.Test(value)
End Function

Public Function Dict() As Object
    Set Dict = CreateObject("Scripting.Dictionary")
    Dict.CompareMode = vbBinaryCompare
End Function

Public Sub Put(ByVal target As Object, ByVal key As String, ByVal value As Variant)
    If target.Exists(key) Then target.Remove key
    target.Add key, value
End Sub

Public Function Txt(ByVal target As Object, ByVal key As String, Optional ByVal fallback As String = "") As String
    Txt = fallback
    If target.Exists(key) Then
        If Not IsNull(target(key)) Then
            If VarType(target(key)) = vbDouble Then Txt = Format$(target(key), "0") Else Txt = CStr(target(key))
        End If
    End If
End Function

Public Function Num(ByVal target As Object, ByVal key As String, Optional ByVal fallback As Double = 0) As Double
    Num = fallback
    If Len(Txt(target, key)) Then Num = CDbl(Txt(target, key))
End Function

Public Function Flag(ByVal target As Object, ByVal key As String) As Boolean
    If target.Exists(key) Then
        If Not IsNull(target(key)) Then Flag = CBool(target(key))
    End If
End Function

Public Function Clone(ByVal value As Object) As Object
    Set Clone = ParseJson(Json(value))
End Function

Public Function ParseJson(ByVal text As String) As Object
    Need Len(text) <= 8388608, "JSON exceeds the supported limit."
    mText = text: mPos = 1: mDepth = 0
    If Left$(mText, 1) = ChrW$(&HFEFF) Then mPos = 2
    Set ParseJson = ReadValue()
    SkipWhite
    Need mPos > Len(mText), "Invalid JSON document."
    mText = ""
End Function

Private Sub SkipWhite()
    Do While mPos <= Len(mText)
        If InStr(1, " " & vbTab & vbCr & vbLf, Mid$(mText, mPos, 1), vbBinaryCompare) = 0 Then Exit Do
        mPos = mPos + 1
    Loop
End Sub

Private Function ReadValue() As Variant
    Dim ch As String, obj As Object, items As Collection, key As String
    Dim start As Long, token As String
    SkipWhite: mDepth = mDepth + 1
    Need mDepth <= 64 And mPos <= Len(mText), "Invalid JSON nesting."
    ch = Mid$(mText, mPos, 1)
    Select Case ch
        Case "{"
            Set obj = Dict(): mPos = mPos + 1: SkipWhite
            If Mid$(mText, mPos, 1) <> "}" Then
                Do
                    key = ReadString(): SkipWhite
                    Need Mid$(mText, mPos, 1) = ":", "Invalid JSON property."
                    Need Not obj.Exists(key), "Duplicate JSON property."
                    mPos = mPos + 1: obj.Add key, ReadValue()
                    SkipWhite: ch = Mid$(mText, mPos, 1): mPos = mPos + 1
                    If ch = "}" Then Exit Do
                    Need ch = ",", "Invalid JSON object."
                    SkipWhite
                Loop
            Else
                mPos = mPos + 1
            End If
            Set ReadValue = obj
        Case "["
            Set items = New Collection: mPos = mPos + 1: SkipWhite
            If Mid$(mText, mPos, 1) <> "]" Then
                Do
                    items.Add ReadValue(): SkipWhite
                    ch = Mid$(mText, mPos, 1): mPos = mPos + 1
                    If ch = "]" Then Exit Do
                    Need ch = ",", "Invalid JSON array."
                Loop
            Else
                mPos = mPos + 1
            End If
            Set ReadValue = items
        Case """"
            ReadValue = ReadString()
        Case "t"
            Need Mid$(mText, mPos, 4) = "true", "Invalid JSON value.": ReadValue = True: mPos = mPos + 4
        Case "f"
            Need Mid$(mText, mPos, 5) = "false", "Invalid JSON value.": ReadValue = False: mPos = mPos + 5
        Case "n"
            Need Mid$(mText, mPos, 4) = "null", "Invalid JSON value.": ReadValue = Null: mPos = mPos + 4
        Case Else
            start = mPos
            Do While mPos <= Len(mText)
                If InStr(1, "-+0123456789.eE", Mid$(mText, mPos, 1), vbBinaryCompare) = 0 Then Exit Do
                mPos = mPos + 1
            Loop
            token = Mid$(mText, start, mPos - start)
            Dim rx As Object: Set rx = CreateObject("VBScript.RegExp")
            rx.Pattern = "^-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?$"
            Need rx.Test(token), "Invalid JSON number."
            ReadValue = Val(token)
    End Select
    mDepth = mDepth - 1
End Function

Private Function ReadString() As String
    Dim result As String, ch As String, code As Long, digits As String
    Need Mid$(mText, mPos, 1) = """", "Expected JSON string.": mPos = mPos + 1
    Do While mPos <= Len(mText)
        ch = Mid$(mText, mPos, 1): mPos = mPos + 1
        If ch = """" Then ReadString = result: Exit Function
        If ch = "\" Then
            ch = Mid$(mText, mPos, 1): mPos = mPos + 1
            Select Case ch
                Case """", "\", "/": result = result & ch
                Case "b": result = result & Chr$(8)
                Case "f": result = result & Chr$(12)
                Case "n": result = result & vbLf
                Case "r": result = result & vbCr
                Case "t": result = result & vbTab
                Case "u"
                    digits = Mid$(mText, mPos, 4)
                    Need Len(digits) = 4, "Invalid JSON escape."
                    code = CLng("&H" & digits): result = result & ChrW$(code): mPos = mPos + 4
                Case Else: Need False, "Invalid JSON escape."
            End Select
        Else
            Need AscW(ch) >= 32 Or AscW(ch) < 0, "Unescaped JSON control."
            result = result & ch
        End If
    Loop
    Need False, "Unterminated JSON string."
End Function

Public Function Quote(ByVal text As String) As String
    Dim i As Long, ch As String, n As Long, result As String
    result = """"
    For i = 1 To Len(text)
        ch = Mid$(text, i, 1): n = AscW(ch)
        Select Case n
            Case 34: result = result & "\"""
            Case 92: result = result & "\\"
            Case 0 To 31: result = result & "\u" & Right$("0000" & Hex$(n), 4)
            Case Else: result = result & ch
        End Select
    Next i
    Quote = result & """"
End Function

Public Function Json(ByVal value As Variant) As String
    Dim result As String, item As Variant, key As Variant, keys As Variant, i As Long, j As Long, swap As String
    If IsObject(value) Then
        If TypeName(value) = "Collection" Then
            result = "["
            For Each item In value
                If Len(result) > 1 Then result = result & ","
                result = result & Json(item)
            Next item
            Json = result & "]"
        Else
            result = "{"
            keys = value.Keys
            For i = 1 To value.Count - 1
                swap = CStr(keys(i)): j = i - 1
                Do While j >= 0
                    If StrComp(CStr(keys(j)), swap, vbBinaryCompare) <= 0 Then Exit Do
                    keys(j + 1) = keys(j): j = j - 1
                Loop
                keys(j + 1) = swap
            Next i
            For Each key In keys
                If Len(result) > 1 Then result = result & ","
                result = result & Quote(CStr(key)) & ":" & Json(value(key))
            Next key
            Json = result & "}"
        End If
    ElseIf IsNull(value) Then
        Json = "null"
    ElseIf VarType(value) = vbBoolean Then
        If value Then Json = "true" Else Json = "false"
    ElseIf VarType(value) = vbString Then
        Json = Quote(CStr(value))
    Else
        If IsNumeric(value) Then
            If CDbl(value) = Fix(CDbl(value)) Then Json = Format$(value, "0") Else Json = Trim$(Str$(value))
        Else
            Need False, "Unsupported JSON value."
        End If
    End If
End Function
