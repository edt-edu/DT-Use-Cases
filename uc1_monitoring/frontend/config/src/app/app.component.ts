/* (c) https://github.com/MontiCore/monticore */
import {Component} from '@angular/core';
import {
  ClientCommandManager,
  ClientCommandWebsocketCommunication,
  CommandManager
} from '@umlp/commonj2ts';
import {MonitoringDTManagerClientImpl} from 'monitoringdt/MonitoringDTManagerClientImpl';
import {MonitoringDTComponentObserverManager} from 'MonitoringDTComponentObserverManager'

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {
  constructor() {
    MonitoringDTManagerClientImpl.init();
    const cmdManager = new ClientCommandManager(new ClientCommandWebsocketCommunication(
      `${document.baseURI.replace(/^http/, 'ws').replace(/\/$/, '')}` + '/umlp/api/command'
    ));

    CommandManager.initMe(cmdManager);
    MonitoringDTComponentObserverManager.init(); // ChangeMe
    (<any>window).websocket_connected = true;
  }


}
