Attribute VB_Name = "SRReceipts"
Option Explicit
Private Declare PtrSafe Function CreateFileW Lib "kernel32" (ByVal name As LongPtr, ByVal access As Long, ByVal share As Long, ByVal security As LongPtr, ByVal creation As Long, ByVal attributes As Long, ByVal template As LongPtr) As LongPtr
Private Declare PtrSafe Function CloseHandle Lib "kernel32" (ByVal handle As LongPtr) As Long
Private Declare PtrSafe Function FlushFileBuffers Lib "kernel32" (ByVal handle As LongPtr) As Long
Private Declare PtrSafe Function MoveFileExW Lib "kernel32" (ByVal source As LongPtr, ByVal target As LongPtr, ByVal flags As Long) As Long
Private fileLock As LongPtr

Private Function ReceiptDirectory() As String
    Dim fso As Object, path As String
    Set fso = CreateObject("Scripting.FileSystemObject")
    path = Environ$("LOCALAPPDATA"): Need Len(path) > 0, "Windows local application-data directory is unavailable."
    path = path & "\ShiftReportExcel"
    If Not fso.FolderExists(path) Then fso.CreateFolder path
    ReceiptDirectory = path
End Function

Private Function ReceiptPath() As String
    ReceiptPath = ReceiptDirectory() & "\" & ProjectId() & ".receipts.json"
End Function

Public Sub LockReceipts()
    Dim path As String
    If fileLock <> 0 Then Exit Sub
    path = ReceiptDirectory() & "\" & ProjectId() & ".lock"
    fileLock = CreateFileW(StrPtr(path), &HC0000000, 0, 0, 4, &H80, 0)
    If fileLock = -1 Then fileLock = 0
    Need fileLock <> 0, "Another Excel session is using this project's delivery ledger. Finish it first."
End Sub

Public Sub UnlockReceipts()
    If fileLock <> 0 Then CloseHandle fileLock: fileLock = 0
End Sub

Public Function LoadReceipts() As Object
    Dim fso As Object, raw() As Byte, result As Object, records As New Collection
    LockReceipts: Set fso = CreateObject("Scripting.FileSystemObject")
    If fso.FileExists(ReceiptPath()) Then
        raw = ReadBytes(ReceiptPath()): Set result = ParseJson(TextUtf8(raw))
        Need Txt(result, "project") = ProjectId() And Num(result, "version") = 1, "Receipt ledger identity/version mismatch. Preserve it; do not reset to retry."
    Else
        Set result = Dict(): Put result, "version", 1: Put result, "project", ProjectId(): Put result, "records", records
    End If
    Set LoadReceipts = result
End Function

Private Sub Persist(ByVal ledger As Object)
    Dim temporary As String, target As String, raw() As Byte, handle As LongPtr, fso As Object
    target = ReceiptPath(): temporary = target & "." & NewId() & ".tmp": raw = Utf8(Json(ledger))
    WriteBytes temporary, raw
    handle = CreateFileW(StrPtr(temporary), &HC0000000, 0, 0, 3, &H80, 0)
    Need handle <> -1, "Cannot flush the delivery ledger."
    Dim status As Long: status = FlushFileBuffers(handle): CloseHandle handle
    Need status <> 0, "Cannot persist the delivery ledger."
    Need MoveFileExW(StrPtr(temporary), StrPtr(target), 9) <> 0, "Cannot replace the delivery ledger; no retry is safe."
    DisplayReceipts ledger
End Sub

Public Function BeginReceipt(ByVal kind As String, ByVal shiftId As String, ByVal chat As String, ByVal topic As String, ByVal content As String, Optional ByVal snapshotId As String = "") As String
    Dim ledger As Object, records As Collection, row As Variant, route As String, record As Object
    Set ledger = LoadReceipts(): Set records = ledger("records")
    route = Digest(kind & "|" & shiftId & "|" & chat & "|" & topic)
    For Each row In records
        If Txt(row, "route") = route Then
            Need Txt(row, "status") <> "UNKNOWN", "An UNKNOWN operation blocks this shift/destination. Inspect Telegram, then explicitly resolve its receipt."
            If Txt(row, "status") = "SENT" And Txt(row, "content") = content And kind = "report" Then
                Need MsgBox("This exact report was already delivered. Do you explicitly approve an additional copy?", vbYesNo + vbQuestion + vbDefaultButton2) = vbYes, "Additional copy cancelled."
            End If
        End If
    Next row
    Set record = Dict(): BeginReceipt = NewId()
    Put record, "id", BeginReceipt: Put record, "kind", kind: Put record, "shiftId", shiftId
    Put record, "route", route: Put record, "content", content: Put record, "actor", Actor()
    Put record, "at", EpochMs(): Put record, "status", "UNKNOWN": Put record, "snapshotId", snapshotId
    records.Add record: Persist ledger 'Must succeed before any potentially ambiguous network write.
End Function

Public Sub ConfirmReceipt(ByVal id As String, ByVal messageId As String)
    Dim ledger As Object, row As Variant, found As Boolean
    Need Len(messageId) > 0, "Missing confirmed Telegram message identity."
    Set ledger = LoadReceipts()
    For Each row In ledger("records")
        If Txt(row, "id") = id Then
            Need Txt(row, "status") = "UNKNOWN", "Receipt state changed."
            Put row, "status", "SENT": Put row, "messageId", messageId: found = True
        End If
    Next row
    Need found, "Receipt not found. Preserve the confirmed Telegram result; do not resend."
    On Error GoTo BookkeepingFailed
    Persist ledger: Exit Sub
BookkeepingFailed:
    Need False, "Telegram confirmed the write, but the local receipt could not be updated. It remains UNKNOWN. Inspect Telegram; do not resend blindly."
End Sub

Public Sub DisplayReceipts(ByVal ledger As Object)
    Dim ws As Worksheet, row As Variant, r As Long
    Set ws = Sheet("AppReceipts"): ws.Unprotect: ws.Range("A2:I10000").ClearContents
    ws.Range("A2:I10000").NumberFormat = "@": r = 2
    For Each row In ledger("records")
        ws.Cells(r, 1).Value2 = Txt(row, "id"): ws.Cells(r, 2).Value2 = Txt(row, "kind")
        ws.Cells(r, 3).Value2 = Txt(row, "status"): ws.Cells(r, 4).Value2 = Txt(row, "at")
        ws.Cells(r, 5).Value2 = Txt(row, "actor"): ws.Cells(r, 6).Value2 = Txt(row, "shiftId")
        ws.Cells(r, 7).Value2 = Txt(row, "messageId"): ws.Cells(r, 8).Value2 = Txt(row, "snapshotId")
        ws.Cells(r, 9).Value2 = Txt(row, "route"): r = r + 1
    Next row
    ws.Protect UserInterfaceOnly:=True, AllowFiltering:=True
End Sub

Public Sub ResolveReceipt()
    Dim ledger As Object, state As Object, doctors As Object, actor As Object, indexId As String
    Dim id As Variant, row As Variant, answer As Variant, found As Boolean, admin As Boolean
    Set state = ReadIndex(indexId): Set doctors = FetchDoctors(state): Set actor = Authorize(doctors)
    Set ledger = LoadReceipts(): DisplayReceipts ledger: Sheet("AppReceipts").Activate
    id = Application.InputBox("Copy the UNKNOWN receipt UUID from column A. Inspect the actual Telegram destination and project index first.", "Resolve uncertain operation", Type:=2)
    If VarType(id) = vbBoolean Then Exit Sub
    For Each row In ledger("records")
        If Txt(row, "id") = CStr(id) Then
            found = True: Need Txt(row, "status") = "UNKNOWN", "This operation is already resolved."
            admin = Num(actor, "#") > 0 And (Len(Txt(actor, "options")) = 0 Or LCase$(Txt(actor, "options")) = "a" & Txt(actor, "#"))
            Need Txt(row, "actor") = Actor() Or admin, "Only the original sender or a current registry admin can resolve this operation."
            answer = Application.InputBox("After inspecting Telegram, type SENT or NOT_SENT. Absence from the bounded journal alone does not prove failure.", "Explicit resolution", Type:=2)
            If VarType(answer) = vbBoolean Then Exit Sub
            Need CStr(answer) = "SENT" Or CStr(answer) = "NOT_SENT", "Choose SENT or NOT_SENT."
            Need MsgBox("Confirm the inspected result: " & CStr(answer) & "? This does not delete anything in Telegram.", vbYesNo + vbQuestion + vbDefaultButton2) = vbYes, "Resolution cancelled."
            Put row, "status", CStr(answer): Put row, "resolvedBy", Actor(): Put row, "resolvedAt", EpochMs()
        End If
    Next row
    Need found, "Receipt UUID not found."
    Persist ledger
End Sub
