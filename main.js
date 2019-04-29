<script  type="text/javascript">
    function checkIfVideoIsAvailable() {
        console.log("<AM> isRewardedVideoAvailable = " + AppmediationSDK.isRewardedVideoAvailable());
    }

    function appmediationJsInterfaceInit() {
        console.log("<AM> You can use appmediation SDK now");
        AppmediationSDK.isRewardedVideoAvailable();
    }

    function onRewardedVideoClicked() {
        console.log("<AM> User click rewarded video ad");
    }

    function onRewardedVideoClosed() {
        console.log("<AM> User closed rewarded video ad");
        document.getElementById("WatchVideoButton").style.display = 'none';
        setMessageText("Looking for video");
    }

    function onRewardedVideoCompleted(currency, amount) {
        console.log("<AM> User finished watching rewarded video ad till the end. You can reward him: " + amount + " " + currency);
        location.href = "/pointbank.html";
    }

    function onRewardedVideoFailed(error) {
        console.log("<AM> Something went wrong, rewarded video is not available: " + error);
        document.getElementById("WatchVideoButton").style.display = 'none';
        setMessageText(error);
    }

    function onRewardedVideoLoaded(currency, amount) {
        console.log("<AM> New rewarded video is now available. After watching user should receive: " + amount + " " + currency);
        document.getElementById("WatchVideoButton").style.display = 'block';
        setMessageText(amount + " " + currency);
    }

    function onRewardedVideoShowed() {
        console.log("<AM> User is now should be able to see rewarded video ad");
    }

    function setMessageText(text) {
        document.getElementById("extra_message").innerHTML = text;
    }
</script>
