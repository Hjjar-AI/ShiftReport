"""One local process per data directory; never terminate an existing instance."""
import os
from pathlib import Path


def acquire(directory):
    directory=Path(directory)
    directory.mkdir(mode=0o700,parents=True,exist_ok=True)
    path=directory/'.server.lock'
    descriptor=os.open(str(path),os.O_RDWR|os.O_CREAT,0o600)
    stream=os.fdopen(descriptor,'r+b')
    try:
        if os.name=='nt':
            import msvcrt
            if path.stat().st_size==0:
                stream.write(b'0');stream.flush()
            stream.seek(0)
            msvcrt.locking(stream.fileno(),msvcrt.LK_NBLCK,1)
        else:
            import fcntl
            fcntl.flock(stream.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
    except OSError:
        stream.close()
        raise SystemExit('ShiftReport is already using this data directory. Open its existing local address.') from None
    return stream
