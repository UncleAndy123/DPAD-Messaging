import com.klinker.android.send_message.MmsReceivedReceiver
import android.content.Context
import android.net.Uri

class TestReceiver : MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context?, messageUri: Uri?) {}
    override fun onError(context: Context?, error: String?) {}
}
