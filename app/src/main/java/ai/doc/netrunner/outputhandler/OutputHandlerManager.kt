package ai.doc.netrunner.outputhandler

object OutputHandlerManager {

    // Add Your Custom Handlers Here
    // Custom handler must extend [Fragment] and conform to [OutputHandler]

    fun registerHandlers() {
        registerHandler(DefaultOutputHandler.type, DefaultOutputHandler::class.java)
        registerHandler(MobileNetClassificationOutputHandler.type, MobileNetClassificationOutputHandler::class.java)
    }

    // Retrieve Handler

    fun handlerForType(type: String?): Class<out OutputHandler> {
        if (type == null) {
            return DefaultOutputHandler::class.java
        }

        handlers[type]?.let {
            return it
        }

        return DefaultOutputHandler::class.java
    }

    // Private

    private val handlers: MutableMap<String, Class<out OutputHandler>> = HashMap()

    private fun registerHandler(type: String, handler: Class<out OutputHandler>) {
        handlers.put(type, handler)
    }

}