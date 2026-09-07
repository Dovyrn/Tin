use block2::RcBlock;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLCommandBuffer;
use std::ptr::NonNull;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;

pub struct Fence {
    done: Arc<(Mutex<bool>, Condvar)>,
}

impl Fence {
    pub fn new(cmd: &ProtocolObject<dyn MTLCommandBuffer>) -> Self {
        let done = Arc::new((Mutex::new(false), Condvar::new()));
        let signal = done.clone();
        Self::on_complete(
            cmd,
            Box::new(move || {
                *signal.0.lock().unwrap() = true;
                signal.1.notify_all();
            }),
        );
        Self { done }
    }

    pub fn on_complete(cmd: &ProtocolObject<dyn MTLCommandBuffer>, done: Box<dyn FnOnce() + Send>) {
        let done = Mutex::new(Some(done));
        let block = RcBlock::new(move |_cmd: NonNull<ProtocolObject<dyn MTLCommandBuffer>>| {
            if let Some(done) = done.lock().unwrap().take() {
                done();
            }
        });
        unsafe {
            cmd.addCompletedHandler(&*block as *const _ as *mut _);
        }
    }

    pub fn wait(&self, timeout_ns: u64) -> bool {
        let (lock, signal) = &*self.done;
        let guard = lock.lock().unwrap();
        let (guard, _) = signal.wait_timeout_while(guard, Duration::from_nanos(timeout_ns), |done| !*done).unwrap();
        *guard
    }
}
