Attribute VB_Name = "Module1"
Option Explicit
Private working As Boolean

'Keep the original public button names; implementation is separated by responsibility.
Public Sub SaveData()
    Dispatch "publish"
End Sub
Public Sub LoadData()
    Dispatch "retrieve"
End Sub
Public Sub SaveLocal()
    Dispatch "save"
End Sub
Public Sub OpenProject()
    Dispatch "connect"
End Sub
Public Sub VerifyClinician()
    Dispatch "login"
End Sub
Public Sub ImportOriginalRows()
    Dispatch "import"
End Sub
Public Sub SendGeneralReportPDF()
    Dispatch "general"
End Sub
Public Sub SendSupervisorReportPDF()
    Dispatch "supervisor"
End Sub
Public Sub ExportReportPDF()
    Dispatch "export"
End Sub
Public Sub SortPatients()
    Dispatch "sort"
End Sub
Public Sub ShowDeliveryReceipts()
    Dispatch "receipts"
End Sub
Public Sub ResolveDeliveryReceipt()
    Dispatch "resolve"
End Sub
Public Sub SignOut()
    Dispatch "logout"
End Sub
Public Sub SendDoctor1PDF()
    Dispatch "slot1"
End Sub
Public Sub SendDoctor2PDF()
    Dispatch "slot2"
End Sub
Public Sub SendDoctor3PDF()
    Dispatch "slot3"
End Sub
Public Sub SendDoctor4PDF()
    Dispatch "slot4"
End Sub
Public Sub SendDoctor5PDF()
    Dispatch "slot5"
End Sub

Private Sub Dispatch(ByVal action As String)
    Dim ledger As Object, ws As Worksheet, r As Long, index As Long, required As Long
    If working Then Exit Sub
    working = True
    On Error GoTo Failed
    Application.StatusBar = "ShiftReport - " & action
    Select Case action
        Case "connect": OpenProtectedProject
        Case "login": LoginProject
        Case "retrieve": RetrieveData
        Case "import": ImportInitialRows
        Case "save": SaveOnly
        Case "publish": SaveAndPublish
        Case "general": SendReport
        Case "supervisor": ChooseSupervisorReport
        Case "export": SendReport "", True
        Case "sort": SortGrid
        Case "receipts"
            Set ledger = LoadReceipts(): DisplayReceipts ledger: Sheet("AppReceipts").Activate
        Case "resolve": ResolveReceipt
        Case "logout": DisconnectProject: CleanupPreviews: MsgBox "Signed out. Connection credentials were held in memory only.", vbInformation
        Case "slot1", "slot2", "slot3", "slot4", "slot5"
            required = CLng(Right$(action, 1)): Set ws = Sheet("AppDoctors")
            For r = 2 To ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
                If CStr(ws.Cells(r, 3).Value2) = "SUPERVISOR" Then
                    index = index + 1
                    If index = required Then SendReport CStr(ws.Cells(r, 1).Value2): Exit For
                End If
            Next r
            Need index >= required, "No supervisor in that slot. Use the unlimited supervisor selector."
    End Select
Done:
    UnlockReceipts: Application.StatusBar = False: working = False
    Exit Sub
Failed:
    'Transport/crypto replace OS errors with credential-free descriptions.
    MsgBox Err.Description, vbExclamation, "ShiftReport - operation stopped"
    Resume Done
End Sub

Public Sub InitializeWorkbook()
    Dim ws As Worksheet, names As Variant, actions As Variant, captions As Variant
    Dim i As Long, shape As Shape, left As Double, top As Double, macro As String
    On Error GoTo Failed
    Need Val(Application.Version) >= 14, "Excel 2010 or later with VBA7 is required."
    Set ws = Sheet("AppHome"): ws.Unprotect
    For i = ws.Shapes.Count To 1 Step -1
        If Left$(ws.Shapes(i).Name, 3) = "SR_" Then ws.Shapes(i).Delete
    Next i
    actions = Array("OpenProject", "VerifyClinician", "LoadData", "SaveLocal", "SaveData", "SendGeneralReportPDF", "SendSupervisorReportPDF", "ExportReportPDF", "SortPatients", "ShowDeliveryReceipts", "ResolveDeliveryReceipt", "ImportOriginalRows", "SignOut")
    captions = Array("connect", "login", "retrieve", "save", "publish", "send", "supervisorpdf", "export", "sort", "receipts", "resolve", "initial", "logout")
    For i = 0 To UBound(actions)
        left = 22 + (i Mod 3) * 205: top = 135 + (i \ 3) * 52
        Set shape = ws.Shapes.AddShape(msoShapeRoundedRectangle, left, top, 190, 40)
        shape.Name = "SR_" & actions(i): shape.Fill.ForeColor.RGB = RGB(239, 246, 250)
        shape.Line.ForeColor.RGB = RGB(206, 222, 232)
        shape.TextFrame.Characters.Text = Ui(CStr(captions(i)))
        shape.TextFrame.Characters.Font.Name = "Tajawal": shape.TextFrame.Characters.Font.Size = 12
        shape.TextFrame.Characters.Font.Color = RGB(28, 58, 76)
        shape.TextFrame.HorizontalAlignment = xlHAlignCenter: shape.TextFrame.VerticalAlignment = xlVAlignCenter
        macro = "'" & Replace(ThisWorkbook.Name, "'", "''") & "'!Module1." & CStr(actions(i))
        shape.OnAction = macro
    Next i
    Sheet("AppState").Visible = xlSheetVeryHidden: Sheet("AppMeasure").Visible = xlSheetVeryHidden
    ws.Range("B20").Locked = False
    ws.Protect UserInterfaceOnly:=True, DrawingObjects:=True
    Exit Sub
Failed:
    MsgBox "Open the prepared workbook and import all supplied modules. Excel 2010+ for Windows is required. No Telegram call was made.", vbExclamation
End Sub
