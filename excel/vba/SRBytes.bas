Attribute VB_Name = "SRBytes"
Option Explicit
Private Declare PtrSafe Function CoCreateGuid Lib "ole32" (ByRef guid As Any) As Long
Private Declare PtrSafe Sub GetSystemTime Lib "kernel32" (ByRef systemTime As Any)

Public Function Utf8(ByVal text As String) As Byte()
    Dim stream As Object: Set stream = CreateObject("ADODB.Stream")
    stream.Type = 2: stream.Charset = "utf-8": stream.Open: stream.WriteText text
    stream.Position = 0: stream.Type = 1: stream.Position = 3
    Utf8 = stream.Read: stream.Close
End Function

Public Function TextUtf8(ByRef bytes() As Byte) As String
    Dim stream As Object: Set stream = CreateObject("ADODB.Stream")
    stream.Type = 1: stream.Open: stream.Write bytes: stream.Position = 0
    stream.Type = 2: stream.Charset = "utf-8": TextUtf8 = stream.ReadText: stream.Close
End Function

Public Function ByteCount(ByRef bytes() As Byte) As Long
    On Error GoTo EmptyArray
    ByteCount = UBound(bytes) - LBound(bytes) + 1
EmptyArray:
End Function

Public Function Slice(ByRef bytes() As Byte, ByVal start As Long, ByVal count As Long) As Byte()
    Dim result() As Byte, i As Long
    Need start >= 0 And count >= 0 And start + count <= ByteCount(bytes), "Invalid byte envelope."
    If count = 0 Then Slice = result: Exit Function
    ReDim result(0 To count - 1)
    For i = 0 To count - 1: result(i) = bytes(start + i): Next i
    Slice = result
End Function

Public Function Cat(ByRef a() As Byte, ByRef b() As Byte) As Byte()
    Dim result() As Byte, i As Long, na As Long, nb As Long
    na = ByteCount(a): nb = ByteCount(b)
    If na + nb = 0 Then Cat = result: Exit Function
    ReDim result(0 To na + nb - 1)
    For i = 0 To na - 1: result(i) = a(i): Next i
    For i = 0 To nb - 1: result(na + i) = b(i): Next i
    Cat = result
End Function

Public Function Base64(ByRef bytes() As Byte) As String
    Dim node As Object: Set node = CreateObject("MSXML2.DOMDocument.6.0").createElement("b")
    node.DataType = "bin.base64": node.nodeTypedValue = bytes
    Base64 = Replace(Replace(node.Text, vbCr, ""), vbLf, "")
End Function

Public Function Unbase64(ByVal text As String) As Byte()
    Dim node As Object: Set node = CreateObject("MSXML2.DOMDocument.6.0").createElement("b")
    node.DataType = "bin.base64": node.Text = text: Unbase64 = node.nodeTypedValue
End Function

Public Function ReadBytes(ByVal path As String) As Byte()
    Dim stream As Object: Set stream = CreateObject("ADODB.Stream")
    stream.Type = 1: stream.Open: stream.LoadFromFile path
    Need stream.Size <= 8388608, "File exceeds 8 MiB."
    ReadBytes = stream.Read: stream.Close
End Function

Public Sub WriteBytes(ByVal path As String, ByRef bytes() As Byte)
    Dim stream As Object: Set stream = CreateObject("ADODB.Stream")
    stream.Type = 1: stream.Open: stream.Write bytes: stream.SaveToFile path, 2: stream.Close
End Sub

Public Function NewId() As String
    Dim g(0 To 15) As Byte, i As Long, result As String
    Need CoCreateGuid(g(0)) = 0, "Cannot create an identifier."
    'Byte order is irrelevant to uniqueness; output is a valid UUID string.
    For i = 0 To 15
        If i = 4 Or i = 6 Or i = 8 Or i = 10 Then result = result & "-"
        result = result & LCase$(Right$("0" & Hex$(g(i)), 2))
    Next i
    NewId = result
End Function

Public Function EpochMs() As Double
    Dim st(0 To 7) As Integer, utc As Date
    GetSystemTime st(0)
    utc = DateSerial(st(0), st(1), st(3)) + TimeSerial(st(4), st(5), st(6))
    EpochMs = CDbl(DateDiff("d", #1/1/1970#, DateSerial(st(0), st(1), st(3)))) * 86400000# + st(4) * 3600000# + st(5) * 60000# + st(6) * 1000# + st(7)
End Function

Public Function ShiftToday() As String
    Dim utc As Date
    utc = #1/1/1970# + EpochMs() / 86400000#
    'Current project zone Asia/Damascus is UTC+03; no workstation timezone dependence.
    ShiftToday = Format$(utc + (3# - 8.5) / 24#, "yyyy-mm-dd")
End Function

Public Function IsoDate(ByVal value As Variant) As String
    Dim parsed As Date, text As String
    If IsNumeric(value) And VarType(value) <> vbString Then
        parsed = #12/30/1899# + CDbl(value)
    Else
        text = CStr(value)
        Need Len(text) = 10 And Mid$(text, 5, 1) = "-" And Mid$(text, 8, 1) = "-", "Use dates YYYY-MM-DD."
        parsed = DateSerial(CInt(Left$(text, 4)), CInt(Mid$(text, 6, 2)), CInt(Right$(text, 2)))
        Need Format$(parsed, "yyyy-mm-dd") = text, "Invalid calendar date."
    End If
    IsoDate = Format$(parsed, "yyyy-mm-dd")
End Function

Public Function TaskTime(ByVal millis As Double) As String
    If millis > 0 Then TaskTime = Format$(#1/1/1970# + (millis + 10800000#) / 86400000#, "yyyy-mm-dd hh:nn")
End Function

Public Function TaskMillis(ByVal text As String) As Double
    Dim d As Date
    Need Len(text) = 16 And Mid$(text, 11, 1) = " " And Mid$(text, 14, 1) = ":", "Use task deadline YYYY-MM-DD HH:MM in Damascus time."
    d = DateSerial(CInt(Left$(text, 4)), CInt(Mid$(text, 6, 2)), CInt(Mid$(text, 9, 2))) + TimeSerial(CInt(Mid$(text, 12, 2)), CInt(Right$(text, 2)), 0)
    Need Format$(d, "yyyy-mm-dd hh:nn") = text, "Invalid task date/time."
    TaskMillis = Fix((CDbl(d - #1/1/1970#) * 86400000# - 10800000#) / 60000# + 0.5) * 60000#
End Function
