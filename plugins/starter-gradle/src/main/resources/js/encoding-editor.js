/**
 * This is the JavaScript (ES6) Module for the plugin. It makes up the frontend part of the plugin.
 * <p>
 * Each plugin module <b>must</b> have a default class, which is the class being instatiated after
 * loading the moduel from the server.
 * <p>
 * The constructor can accept an API object, which provides methods to access the REST API of the
 * plugin on the server.
 * <p>
 * The bind method is resonsible for wiring up the custom editor plugin with its surrounding. It
 * can (and should) create HTML elements (and return the root of them), fill the with initial values,
 * and bind listeners to elements to be able to fire update events when required.
 * <p>
 * This starter plugin will create a single editor which will be filled with a value decoded (on the
 * server) from a base64 encoded value. When the user changes the value, the server will encode the
 * new value and an update event is fired. While server calls are running, the state is set to invalid
 * as well, so the user cannot confirm the dialog too fast.
 */
export default class Plugin {

    constructor(api) {
        this.api = api;
        this.onInput = this.onInput.bind(this);
    }

    onInput(e) {
    	this.onValidStateChange(false);

        this.api.get('starter/encode', {v:e.target.value}).then(r => {
            this.onUpdate(r);
            this.onValidStateChange(true);
        })
    }

    bind(onRead, onUpdate, onValidStateChange) {
        this.onUpdate = onUpdate;
        this.onValidStateChange = onValidStateChange;
        
        this.onValidStateChange(false);
        this.container = document.createElement('div');
        let shadow = this.container.attachShadow({mode: 'open'});
        shadow.innerHTML = `
            <link rel="stylesheet" href="${this.api.getResourceUrl()}/js/input.css"/>
            
            <div class="container">
			    <div class="group" id='mygroup'>      
			      <input id='mycontrol' type="text" required>
			      <span class="bar"></span>
			      <label>NEW Text To Encrypt</label>
			    </div>
		    </div>
        `

        let input = shadow.querySelector('#mycontrol');
        input.addEventListener('input', this.onInput);
        input.setAttribute('disabled', '');

        input.value = 'Loading...';

        this.api.get('starter/decode', {v:onRead()}).then(r => {
            input.value = r;
            this.onValidStateChange(true);
            input.removeAttribute('disabled');
        })

        return this.container;
    }

}