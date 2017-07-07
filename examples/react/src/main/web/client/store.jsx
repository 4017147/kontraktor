import AppDispatcher from './dispatcher.jsx';
import { EventEmitter } from 'events';
import AppActions from './actions.jsx';

class AppStore extends EventEmitter {

  constructor() {
    super();
    this.loggedIn = false;
    this.userData = {};
    this.dispatchToken = AppDispatcher.register(this.dispatcherCallback.bind(this));
    this.client = new KClient();
    this.server = null;
    this.session = null;
  }

  getServer() {
    return this.server;
  }

  getSession() {
    return this.session;
  }

  isLoggedIn() {
    return this.loggedIn;
  }

  getUserData() {
    return this.userData;
  }

  login(user,password) {
    if ( ! this.isLoggedIn() ) {
      this.client.connect("http://localhost:8080/ep").then( (r,e) => {
        if ( r ) {
          this.server = r;
          AppActions.connected();
          r.ask("login",user,password, null).then( (arr,err) => {
            console.log("login ",arr);
            if ( arr && arr[0] ) {
              this.session = arr[0];
              this.userData = arr[1];
              this.emit("STORE_LOGIN_CHANGED");
            }
          });
        }
      });
    }
  }

  addChangeListener(eventName, callback) {
    this.on(eventName, callback);
  }

  dispatcherCallback(action) {
    switch (action.actionType) {
      case 'TRY_LOGIN':
        this.login(action.value.user,action.value.password);
        break;
      case 'LOGIN_CHANGED':
        // this.emit(action.value);
        break;
    }
    this.emit('STORE_' + action.actionType);
    return true;
  }
}

export default new AppStore();
