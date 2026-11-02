Attribute VB_Name = "SRCrypto"
Option Explicit
Private Declare PtrSafe Function BCryptOpenAlgorithmProvider Lib "bcrypt" (ByRef handle As LongPtr, ByVal alg As LongPtr, ByVal implementation As LongPtr, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptCloseAlgorithmProvider Lib "bcrypt" (ByVal handle As LongPtr, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptSetProperty Lib "bcrypt" (ByVal handle As LongPtr, ByVal prop As LongPtr, ByRef value As Any, ByVal size As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptGenerateSymmetricKey Lib "bcrypt" (ByVal alg As LongPtr, ByRef key As LongPtr, ByVal keyObject As LongPtr, ByVal objectSize As Long, ByRef secret As Any, ByVal secretSize As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptDestroyKey Lib "bcrypt" (ByVal key As LongPtr) As Long
Private Declare PtrSafe Function BCryptEncrypt Lib "bcrypt" (ByVal key As LongPtr, ByRef source As Any, ByVal sourceSize As Long, ByRef auth As Any, ByVal iv As LongPtr, ByVal ivSize As Long, ByRef target As Any, ByVal targetSize As Long, ByRef written As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptDecrypt Lib "bcrypt" (ByVal key As LongPtr, ByRef source As Any, ByVal sourceSize As Long, ByRef auth As Any, ByVal iv As LongPtr, ByVal ivSize As Long, ByRef target As Any, ByVal targetSize As Long, ByRef written As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptDeriveKeyPBKDF2 Lib "bcrypt" (ByVal alg As LongPtr, ByRef password As Any, ByVal passwordSize As Long, ByRef salt As Any, ByVal saltSize As Long, ByVal iterations As Currency, ByRef target As Any, ByVal targetSize As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptGenRandom Lib "bcrypt" (ByVal handle As LongPtr, ByRef target As Any, ByVal size As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptCreateHash Lib "bcrypt" (ByVal alg As LongPtr, ByRef hash As LongPtr, ByVal hashObject As LongPtr, ByVal objectSize As Long, ByVal secret As LongPtr, ByVal secretSize As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptHashData Lib "bcrypt" (ByVal hash As LongPtr, ByRef source As Any, ByVal size As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptFinishHash Lib "bcrypt" (ByVal hash As LongPtr, ByRef target As Any, ByVal size As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Function BCryptDestroyHash Lib "bcrypt" (ByVal hash As LongPtr) As Long
Private Declare PtrSafe Sub CopyMemory Lib "kernel32" Alias "RtlMoveMemory" (ByRef target As Any, ByRef source As Any, ByVal size As LongPtr)

Private Sub Check(ByVal status As Long)
    Need status = 0, "Windows cryptography failed; no plaintext fallback is permitted."
End Sub

Public Function RandomBytes(ByVal size As Long) As Byte()
    Dim result() As Byte: ReDim result(0 To size - 1)
    Check BCryptGenRandom(0, result(0), size, 2): RandomBytes = result
End Function

Public Function Sha256(ByRef bytes() As Byte) As String
    Dim alg As LongPtr, hash As LongPtr, result(0 To 31) As Byte, i As Long
    On Error GoTo Failed
    Check BCryptOpenAlgorithmProvider(alg, StrPtr("SHA256"), 0, 0)
    Check BCryptCreateHash(alg, hash, 0, 0, 0, 0, 0)
    If ByteCount(bytes) Then Check BCryptHashData(hash, bytes(0), ByteCount(bytes), 0)
    Check BCryptFinishHash(hash, result(0), 32, 0)
    For i = 0 To 31: Sha256 = Sha256 & LCase$(Right$("0" & Hex$(result(i)), 2)): Next i
Done:
    If hash Then BCryptDestroyHash hash
    If alg Then BCryptCloseAlgorithmProvider alg, 0
    Exit Function
Failed:
    If hash Then BCryptDestroyHash hash
    If alg Then BCryptCloseAlgorithmProvider alg, 0
    Need False, "Cannot hash the reviewed content."
End Function

Public Function Digest(ByVal text As String) As String
    Dim bytes() As Byte: bytes = Utf8(text): Digest = Sha256(bytes)
End Function

Public Function PasswordKey(ByVal password As String, ByRef salt() As Byte) As Byte()
    Dim alg As LongPtr, raw() As Byte, result(0 To 31) As Byte, status As Long
    raw = Utf8(password): Need ByteCount(raw) > 0 And ByteCount(salt) = 16, "Invalid join password or salt."
    Check BCryptOpenAlgorithmProvider(alg, StrPtr("SHA256"), 0, 8)
    'Currency's in-memory integer is scaled by 10000: 21 represents 210000 iterations.
    status = BCryptDeriveKeyPBKDF2(alg, raw(0), ByteCount(raw), salt(0), 16, CCur(21), result(0), 32, 0)
    BCryptCloseAlgorithmProvider alg, 0
    Erase raw: Check status: PasswordKey = result
End Function

Private Sub LongField(ByRef buffer() As Byte, ByVal offset As Long, ByVal value As Long)
    CopyMemory buffer(offset), value, 4
End Sub

Private Sub PointerField(ByRef buffer() As Byte, ByVal offset As Long, ByVal value As LongPtr)
    #If Win64 Then
        CopyMemory buffer(offset), value, 8
    #Else
        CopyMemory buffer(offset), value, 4
    #End If
End Sub

Public Function Gcm(ByRef source() As Byte, ByRef key() As Byte, ByRef nonce() As Byte, ByRef aad() As Byte, ByRef tag() As Byte, ByVal decrypting As Boolean) As Byte()
    Dim alg As LongPtr, handle As LongPtr, auth() As Byte, result() As Byte
    Dim written As Long, size As Long, status As Long, errorNumber As Long
    On Error GoTo Failed
    size = ByteCount(source)
    Need ByteCount(key) = 32 And ByteCount(nonce) = 12 And size > 0, "Invalid encrypted envelope."
    If decrypting Then Need ByteCount(tag) = 16, "Invalid authentication tag."
    If Not decrypting Then ReDim tag(0 To 15)
    ReDim result(0 To size - 1)
    #If Win64 Then
        ReDim auth(0 To 87)
        LongField auth, 0, 88: LongField auth, 4, 1
        PointerField auth, 8, VarPtr(nonce(0)): LongField auth, 16, 12
        If ByteCount(aad) Then PointerField auth, 24, VarPtr(aad(0))
        LongField auth, 32, ByteCount(aad)
        PointerField auth, 40, VarPtr(tag(0)): LongField auth, 48, 16
    #Else
        ReDim auth(0 To 63)
        LongField auth, 0, 64: LongField auth, 4, 1
        PointerField auth, 8, VarPtr(nonce(0)): LongField auth, 12, 12
        If ByteCount(aad) Then PointerField auth, 16, VarPtr(aad(0))
        LongField auth, 20, ByteCount(aad)
        PointerField auth, 24, VarPtr(tag(0)): LongField auth, 28, 16
    #End If
    Check BCryptOpenAlgorithmProvider(alg, StrPtr("AES"), 0, 0)
    Dim mode As String: mode = "ChainingModeGCM" & vbNullChar
    Check BCryptSetProperty(alg, StrPtr("ChainingMode"), ByVal StrPtr(mode), LenB(mode), 0)
    Check BCryptGenerateSymmetricKey(alg, handle, 0, 0, key(0), 32, 0)
    If decrypting Then
        status = BCryptDecrypt(handle, source(0), size, auth(0), 0, 0, result(0), size, written, 0)
    Else
        status = BCryptEncrypt(handle, source(0), size, auth(0), 0, 0, result(0), size, written, 0)
    End If
    Check status: Need written = size, "Encrypted output length mismatch."
    BCryptDestroyKey handle: handle = 0: BCryptCloseAlgorithmProvider alg, 0: alg = 0
    Gcm = result: Exit Function
Failed:
    If handle Then BCryptDestroyKey handle
    If alg Then BCryptCloseAlgorithmProvider alg, 0
    Erase result
    Need False, "Cannot decrypt/encrypt: wrong key, damaged data or unsupported Windows crypto."
End Function

Public Function ProtectData(ByRef raw() As Byte, ByRef key() As Byte, ByVal purpose As String) As Byte()
    Dim nonce() As Byte, tag() As Byte, aad() As Byte, ciphertext() As Byte, magic() As Byte, tmp() As Byte
    If ByteCount(key) = 0 Then ProtectData = raw: Exit Function
    nonce = RandomBytes(12): magic = Utf8("SRDATA1"): aad = Utf8("SRDATA1" & purpose)
    ciphertext = Gcm(raw, key, nonce, aad, tag, False)
    tmp = Cat(magic, nonce): tmp = Cat(tmp, ciphertext): ProtectData = Cat(tmp, tag)
End Function

Public Function UnprotectData(ByRef raw() As Byte, ByRef key() As Byte, ByVal purpose As String) As Byte()
    Dim nonce() As Byte, tag() As Byte, aad() As Byte, ciphertext() As Byte, prefix() As Byte
    Need ByteCount(raw) >= 7, "Data envelope is truncated."
    prefix = Slice(raw, 0, 7)
    If TextUtf8(prefix) <> "SRDATA1" Then
        Need ByteCount(key) = 0, "Unencrypted data in an encrypted project."
        UnprotectData = raw: Exit Function
    End If
    Need ByteCount(key) = 32 And ByteCount(raw) > 35, "Missing key or truncated encrypted data."
    nonce = Slice(raw, 7, 12): tag = Slice(raw, ByteCount(raw) - 16, 16)
    ciphertext = Slice(raw, 19, ByteCount(raw) - 35): aad = Utf8("SRDATA1" & purpose)
    UnprotectData = Gcm(ciphertext, key, nonce, aad, tag, True)
End Function
