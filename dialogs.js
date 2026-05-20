let confirmOverlayEl = null;
let confirmMessageEl = null;
let confirmOkEl = null;
let confirmCancelEl = null;
let isInitialized = false;

function ensureConfirmRefs() {
  if (isInitialized) return;
  confirmOverlayEl = document.getElementById('confirm-overlay');
  confirmMessageEl = document.getElementById('confirm-message');
  confirmOkEl = document.getElementById('confirm-ok');
  confirmCancelEl = document.getElementById('confirm-cancel');
  isInitialized = true;
}

export function confirmDialog(message) {
  ensureConfirmRefs();
  if (!confirmOverlayEl || !confirmMessageEl || !confirmOkEl || !confirmCancelEl) {
    return Promise.resolve(window.confirm(String(message)));
  }

  return new Promise(resolve => {
    confirmMessageEl.textContent = String(message);
    confirmOverlayEl.hidden = false;

    const onOk = () => {
      cleanup();
      resolve(true);
    };
    const onCancel = () => {
      cleanup();
      resolve(false);
    };
    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCancel();
      }
    };

    function cleanup() {
      confirmOverlayEl.hidden = true;
      confirmOkEl.removeEventListener('click', onOk);
      confirmCancelEl.removeEventListener('click', onCancel);
      document.removeEventListener('keydown', onKeyDown, true);
    }

    confirmOkEl.addEventListener('click', onOk);
    confirmCancelEl.addEventListener('click', onCancel);
    document.addEventListener('keydown', onKeyDown, true);
  });
}
